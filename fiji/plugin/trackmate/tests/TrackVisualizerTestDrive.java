package fiji.plugin.trackmate.tests;

import ij.ImagePlus;

import java.io.File;
import java.io.IOException;

import org.jdom.JDOMException;

import fiji.plugin.trackmate.FeatureFilter;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.TrackMateModel;
import fiji.plugin.trackmate.TrackMate_;
import fiji.plugin.trackmate.features.track.TrackBranchingAnalyzer;
import fiji.plugin.trackmate.io.TmXmlReader;
import fiji.plugin.trackmate.visualization.AbstractTrackMateModelView;
import fiji.plugin.trackmate.visualization.AbstractTrackMateModelView.ViewType;
import fiji.plugin.trackmate.visualization.trackscheme.TrackSchemeFrame;
import fiji.plugin.trackmate.visualization.TrackMateModelView;

public class TrackVisualizerTestDrive {

	private static final File file = new File("/Users/tinevez/Desktop/Data/FakeTracks.xml");
	
	public static void main(String[] args) throws JDOMException, IOException {
	
		ij.ImageJ.main(args);
		
		TmXmlReader reader = new TmXmlReader(file);
		reader.parse();
		
		// Load objects 
		final TrackMateModel model = reader.getModel();
		TrackMate_ plugin = new TrackMate_(model);
		
		System.out.println("Found "+model.getNTracks()+" tracks.");
		for(int i=0; i<model.getNFilteredTracks(); i++) 
			System.out.println(" - "+model.trackToString(i));
		
		FeatureFilter filter = new FeatureFilter(TrackBranchingAnalyzer.NUMBER_SPOTS, 50f, true);
		model.addTrackFilter(filter);
		plugin.execTrackFiltering();
		System.out.println("After filtering, retaining "+model.getNFilteredTracks()+" tracks.");
			
		ImagePlus imp = reader.getImage();
		Settings settings = reader.getSettings();
		settings.segmenterSettings = reader.getSegmenterSettings();
		settings.imp = imp;
		
		// Launch ImageJ and display
		if (null != imp) {
			ij.ImageJ.main(args);
			imp.show();
		}
		
		// Instantiate displayer
		final TrackMateModelView displayer = AbstractTrackMateModelView.instantiateView(ViewType.HYPERSTACK_DISPLAYER, model);
		displayer.refresh();
		
		// Display Track scheme
		final TrackSchemeFrame frame = new TrackSchemeFrame(model);
		frame.setVisible(true);
		
	}
}