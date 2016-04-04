package fiji.plugin.trackmate;

import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fiji.plugin.trackmate.detection.DetectorKeys;
import fiji.plugin.trackmate.detection.LogDetectorFactory;
import fiji.plugin.trackmate.features.edges.EdgeAnalyzer;
import fiji.plugin.trackmate.features.spot.SpotAnalyzerFactory;
import fiji.plugin.trackmate.features.track.TrackAnalyzer;
import fiji.plugin.trackmate.features.track.TrackIndexAnalyzer;
import fiji.plugin.trackmate.gui.GuiUtils;
import fiji.plugin.trackmate.providers.EdgeAnalyzerProvider;
import fiji.plugin.trackmate.providers.SpotAnalyzerProvider;
import fiji.plugin.trackmate.providers.TrackAnalyzerProvider;
import fiji.plugin.trackmate.tracking.TrackerKeys;
import fiji.plugin.trackmate.tracking.sparselap.SimpleSparseLAPTrackerFactory;
import fiji.plugin.trackmate.util.LogRecorder;
import fiji.plugin.trackmate.visualization.PerTrackFeatureColorGenerator;
import fiji.plugin.trackmate.visualization.TrackMateModelView;
import fiji.plugin.trackmate.visualization.ViewFactory;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayerFactory;
import fiji.util.SplitString;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import net.imglib2.util.ValuePair;

public class TrackMateRunner_ extends TrackMatePlugIn_
{

	/*
	 * List of arguments usable in the macro.
	 */

	private static final String ARG_RADIUS = "radius";

	private static final String ARG_THRESHOLD = "threshold";

	private static final String ARG_SUBPIXEL = "subpixel";

	private static final String ARG_MEDIAN = "median";

	private static final String ARG_CHANNEL = "channel";

	private static final String ARG_MAX_DISTANCE = "max_distance";

	private static final String ARG_MAX_GAP_DISTANCE = "max_gap_distance";

	private static final String ARG_MAX_GAP_FRAMES = "max_frame_gap";

	/*
	 * Other fields
	 */

	private Logger logger = new LogRecorder( Logger.DEFAULT_LOGGER );

	@Override
	public void run( String arg )
	{

		logger = new LogRecorder( Logger.IJ_LOGGER );
		logger.log( "Received the following arg string: " + arg + "\n" );

		/*
		 * Check if we have an image.
		 */

		final ImagePlus imp = WindowManager.getCurrentImage();
		if ( null == imp )
		{
			logger.error( "No image selected. Aborting.\n" );
			return;
		}


		if ( !imp.isVisible() )
		{
			imp.setOpenAsHyperStack( true );
			imp.show();
		}
		GuiUtils.userCheckImpDimensions( imp );

		settings = createSettings( imp );
		model = createModel();
		trackmate = createTrackMate();

		/*
		 * Configure default settings.
		 */

		// Default detector.
		settings.detectorFactory = new LogDetectorFactory<>();
		settings.detectorSettings = settings.detectorFactory.getDefaultSettings();

		// Default tracker.
		settings.trackerFactory = new SimpleSparseLAPTrackerFactory();
		settings.trackerSettings = settings.trackerFactory.getDefaultSettings();

		/*
		 * Parse macro arguments.
		 */

		if ( null == arg || arg.isEmpty() )
		{
			final String macroOption = Macro.getOptions();
			if ( null != macroOption )
			{
				logger.log( "Detecting empty arg, catching macro option:\n" + macroOption + '\n' );
				arg = macroOption;
			}
		}

		if ( null != arg )
		{
			final Map< String, ValuePair< String, MacroArgumentConverter > > detectorParsers = prepareDetectorParsableArguments();
			final Map< String, ValuePair< String, MacroArgumentConverter > > trackerParsers = prepareTrackerParsableArguments();

			try
			{
				final Map< String, String > macroOptions = SplitString.splitMacroOptions( arg );
				final Set< String > unknownParameters = new HashSet<>( macroOptions.keySet() );

				/*
				 * Detector parameters.
				 */

				for ( final String parameter : macroOptions.keySet() )
				{
					final String value = macroOptions.get( parameter );
					final ValuePair< String, MacroArgumentConverter > parser = detectorParsers.get( parameter );
					if ( parser == null )
					{
						continue;
					}
					unknownParameters.remove( parameter );

					final String key = parser.getA();
					final MacroArgumentConverter converter = parser.getB();
					try
					{

						final Object val = converter.convert( value );
						settings.detectorSettings.put( key, val );
					}
					catch ( final NumberFormatException nfe )
					{
						logger.error( "Cannot interprete value for parameter " + parameter + ": " + value + ". Skipping.\n" );
						continue;
					}

				}

				/*
				 * Tracker parameters.
				 */

				for ( final String parameter : macroOptions.keySet() )
				{
					final String value = macroOptions.get( parameter );
					final ValuePair< String, MacroArgumentConverter > parser = trackerParsers.get( parameter );
					if ( parser == null )
					{
						continue;
					}
					unknownParameters.remove( parameter );

					final String key = parser.getA();
					final MacroArgumentConverter converter = parser.getB();
					try
					{

						final Object val = converter.convert( value );
						settings.trackerSettings.put( key, val );
					}
					catch ( final NumberFormatException nfe )
					{
						logger.error( "Cannot interprete value for parameter " + parameter + ": " + value + ". Skipping.\n" );
						continue;
					}

				}

				/*
				 * Unknown parameters.
				 */
				if ( !unknownParameters.isEmpty() )
				{
					logger.error( "The following parameters are unkown:\n" );
					for ( final String unknownParameter : unknownParameters )
					{
						logger.error( "  " + unknownParameter );
					}
				}

			}
			catch ( final ParseException e )
			{
				logger.error( "Could not parse plugin option string: " + e.getMessage() + ".\n" );
				e.printStackTrace();
			}

			logger.log( "Final settings object is:\n" + settings );
			if (!trackmate.checkInput() || !trackmate.process())
			{
				logger.error( "Error while performing tracking:\n" + trackmate.getErrorMessage() );
				return;
			}

			/*
			 * Display results.
			 */

			final SelectionModel selectionModel = new SelectionModel( model );

			final ViewFactory displayerFactory = new HyperStackDisplayerFactory();
			final TrackMateModelView view = displayerFactory.create( model, trackmate.getSettings(), selectionModel );
			final PerTrackFeatureColorGenerator trackColor = new PerTrackFeatureColorGenerator( model, TrackIndexAnalyzer.TRACK_INDEX );
			view.setDisplaySettings( TrackMateModelView.KEY_TRACK_COLORING, trackColor );

			view.render();

		}
	}
	
	@Override
	protected Settings createSettings( final ImagePlus imp )
	{
		final Settings s = super.createSettings( imp );

		s.clearSpotAnalyzerFactories();
		final SpotAnalyzerProvider spotAnalyzerProvider = new SpotAnalyzerProvider();
		final List< String > spotAnalyzerKeys = spotAnalyzerProvider.getKeys();
		for ( final String key : spotAnalyzerKeys )
		{
			final SpotAnalyzerFactory< ? > spotFeatureAnalyzer = spotAnalyzerProvider.getFactory( key );
			s.addSpotAnalyzerFactory( spotFeatureAnalyzer );
		}

		s.clearEdgeAnalyzers();
		final EdgeAnalyzerProvider edgeAnalyzerProvider = new EdgeAnalyzerProvider();
		final List< String > edgeAnalyzerKeys = edgeAnalyzerProvider.getKeys();
		for ( final String key : edgeAnalyzerKeys )
		{
			final EdgeAnalyzer edgeAnalyzer = edgeAnalyzerProvider.getFactory( key );
			s.addEdgeAnalyzer( edgeAnalyzer );
		}

		s.clearTrackAnalyzers();
		final TrackAnalyzerProvider trackAnalyzerProvider = new TrackAnalyzerProvider();
		final List< String > trackAnalyzerKeys = trackAnalyzerProvider.getKeys();
		for ( final String key : trackAnalyzerKeys )
		{
			final TrackAnalyzer trackAnalyzer = trackAnalyzerProvider.getFactory( key );
			s.addTrackAnalyzer( trackAnalyzer );
		}

		return s;
	}
	
	/**
	 * Prepare a map of all the arguments that are accepted by this macro for
	 * the detection part.
	 * 
	 * @return a map of parsers that can handle macro parameters.
	 */
	private Map< String, ValuePair< String, MacroArgumentConverter > > prepareDetectorParsableArguments()
	{
		// Map
		final Map< String, ValuePair< String, MacroArgumentConverter > > parsers = new HashMap<>();
		
		// Converters.
		final DoubleMacroArgumentConverter doubleConverter = new DoubleMacroArgumentConverter();
		final IntegerMacroArgumentConverter integerConverter = new IntegerMacroArgumentConverter();
		final BooleanMacroArgumentConverter booleanConverter = new BooleanMacroArgumentConverter();

		// Spot radius.
		final ValuePair< String, MacroArgumentConverter > radiusPair =
				new ValuePair< String, TrackMateRunner_.MacroArgumentConverter >( DetectorKeys.KEY_RADIUS, doubleConverter );
		parsers.put( ARG_RADIUS, radiusPair );
		
		// Spot quality threshold.
		final ValuePair< String, MacroArgumentConverter > thresholdPair =
				new ValuePair< String, TrackMateRunner_.MacroArgumentConverter >( DetectorKeys.KEY_THRESHOLD, doubleConverter );
		parsers.put( ARG_THRESHOLD, thresholdPair );

		// Sub-pixel localization.
		final ValuePair< String, MacroArgumentConverter > subpixelPair =
				new ValuePair< String, TrackMateRunner_.MacroArgumentConverter >( DetectorKeys.KEY_DO_SUBPIXEL_LOCALIZATION, booleanConverter );
		parsers.put( ARG_SUBPIXEL, subpixelPair );

		// Do median filtering.
		final ValuePair< String, MacroArgumentConverter > medianPair =
				new ValuePair< String, TrackMateRunner_.MacroArgumentConverter >( DetectorKeys.KEY_DO_MEDIAN_FILTERING, booleanConverter );
		parsers.put( ARG_MEDIAN, medianPair );

		// Target channel.
		final ValuePair< String, MacroArgumentConverter > channelPair =
				new ValuePair< String, TrackMateRunner_.MacroArgumentConverter >( DetectorKeys.KEY_TARGET_CHANNEL, integerConverter );
		parsers.put( ARG_CHANNEL, channelPair );

		return parsers;
	}
	
	/**
	 * Prepare a map of all the arguments that are accepted by this macro for
	 * the particle-linking part.
	 * 
	 * @return a map of parsers that can handle macro parameters.
	 */
	private Map< String, ValuePair< String, MacroArgumentConverter > > prepareTrackerParsableArguments()
	{
		// Map
		final Map< String, ValuePair< String, MacroArgumentConverter > > parsers = new HashMap<>();

		// Converters.
		final DoubleMacroArgumentConverter doubleConverter = new DoubleMacroArgumentConverter();
		final IntegerMacroArgumentConverter integerConverter = new IntegerMacroArgumentConverter();

		// Max linking distance.
		final ValuePair< String, MacroArgumentConverter > maxDistancePair =
				new ValuePair< String, TrackMateRunner_.MacroArgumentConverter >( TrackerKeys.KEY_LINKING_MAX_DISTANCE, doubleConverter );
		parsers.put( ARG_MAX_DISTANCE, maxDistancePair );

		// Max gap distance.
		final ValuePair< String, MacroArgumentConverter > maxGapDistancePair =
				new ValuePair< String, TrackMateRunner_.MacroArgumentConverter >( TrackerKeys.KEY_GAP_CLOSING_MAX_DISTANCE, doubleConverter );
		parsers.put( ARG_MAX_GAP_DISTANCE, maxGapDistancePair );

		// Target channel.
		final ValuePair< String, MacroArgumentConverter > maxGapFramesPair =
				new ValuePair< String, TrackMateRunner_.MacroArgumentConverter >( TrackerKeys.KEY_GAP_CLOSING_MAX_FRAME_GAP, integerConverter );
		parsers.put( ARG_MAX_GAP_FRAMES, maxGapFramesPair );

		return parsers;
	}
	

	/*
	 * PRIVATE CLASSES AND INTERFACES
	 */

	private static interface MacroArgumentConverter
	{
		public Object convert( String valStr ) throws NumberFormatException;
	}

	private static final class DoubleMacroArgumentConverter implements MacroArgumentConverter
	{
		@Override
		public Object convert( final String valStr ) throws NumberFormatException
		{
			return Double.valueOf( valStr );
		}
	}

	private static final class IntegerMacroArgumentConverter implements MacroArgumentConverter
	{
		@Override
		public Object convert( final String valStr ) throws NumberFormatException
		{
			return Integer.valueOf( valStr );
		}
	}

	private static final class BooleanMacroArgumentConverter implements MacroArgumentConverter
	{
		@Override
		public Object convert( final String valStr ) throws NumberFormatException
		{
			return Boolean.valueOf( valStr );
		}
	}

	/*
	 * MAIN METHOD
	 */

	public static void main( final String[] args )
	{
		ImageJ.main( args );
		IJ.openImage( "samples/FakeTracks.tif" ).show();
		new TrackMateRunner_().run( "radius=2.5 threshold=50.1 subpixel=true median=false channel=1 max_frame_gap=0" );
	}

}
