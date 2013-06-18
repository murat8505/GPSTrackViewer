package de.ronnyfriedland.mytrackvisualizer;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.divbyzero.gpx.Coordinate;
import net.divbyzero.gpx.GPX;
import net.divbyzero.gpx.Track;
import net.divbyzero.gpx.TrackSegment;
import net.divbyzero.gpx.Waypoint;
import net.divbyzero.gpx.parser.JDOM;
import net.divbyzero.gpx.parser.ParsingException;

import org.mapsforge.android.maps.MapActivity;
import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.overlay.ArrayWayOverlay;
import org.mapsforge.android.maps.overlay.OverlayWay;
import org.mapsforge.core.GeoPoint;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import de.ronnyfriedland.mytrackvisualizer.enums.PreferenceKeys;
import de.ronnyfriedland.mytrackvisualizer.to.TrackMetadata;

public class GpsTrackViewerActivity extends MapActivity {

	private MapView mapView;
	private TextView textView;

	private List<TrackMetadata> trackList = new ArrayList<TrackMetadata>();

	/**
	 * {@inheritDoc}
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		mapView = (MapView) findViewById(R.id.mapView);
		textView = (TextView) findViewById(R.id.trackSummary);

		textView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				final Dialog dialog = new Dialog(v.getContext());
				dialog.setTitle(R.string.trk_title);
				// build dialog
				ScrollView sv = new ScrollView(v.getContext());
				LinearLayout ll = new LinearLayout(v.getContext());
				ll.setOrientation(LinearLayout.VERTICAL);
				sv.addView(ll);
				dialog.setContentView(sv);
				for (TrackMetadata track : trackList) {
					// add name
					addElem(ll, getResources().getString(R.string.trk_name), track.getName());
					// add date
					SimpleDateFormat sdfDestination = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN);
					addElem(ll, getResources().getString(R.string.trk_date), sdfDestination.format(track.getDate()));
					// add length
					addElem(ll, getResources().getString(R.string.trk_length),
							String.format("%.2f m", track.getLength()));
				}
				dialog.show();
			}

			private void addElem(LinearLayout ll, final String key, final String value) {
				TextView tvName = new TextView(ll.getContext());
				tvName.setText(key);
				ll.addView(tvName);

				EditText etName = new EditText(ll.getContext());
				etName.setText(value);
				ll.addView(etName);
			}
		});
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.map_menu, menu);
		return true;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			startActivity(new Intent(getBaseContext(), GpsTrackViewerSettingsActivity.class));
			return true;
		case R.id.action_exit:
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see org.mapsforge.android.maps.MapActivity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();

		loadContent();
	}

	private void loadContent() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		loadMap(prefs);
		loadTrack(prefs);
	}

	private void loadMap(SharedPreferences prefs) {
		String mapFileName = prefs.getString(PreferenceKeys.MAP.getKey(), "");

		File mapFile = new File(Environment.getExternalStorageDirectory(), mapFileName);
		if (mapFile.exists() && mapFile.isFile()) {
			mapView.setMapFile(mapFile);
		} else {
			Toast.makeText(getBaseContext(), getResources().getString(R.string.msg_no_map), Toast.LENGTH_LONG).show();
		}
		mapView.setClickable(true);
		mapView.setBuiltInZoomControls(true);
		mapView.getController().setZoom(12);
	}

	private void loadTrack(SharedPreferences prefs) {
		String gpxFileName = prefs.getString(PreferenceKeys.TRACK.getKey(), "");

		Paint wayDefaultPaintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
		wayDefaultPaintFill.setStyle(Paint.Style.STROKE);
		wayDefaultPaintFill.setColor(getResources().getColor(android.R.color.holo_blue_dark));
		wayDefaultPaintFill.setAlpha(160);
		wayDefaultPaintFill.setStrokeWidth(7);
		wayDefaultPaintFill.setStrokeJoin(Paint.Join.ROUND);
		wayDefaultPaintFill.setPathEffect(new DashPathEffect(new float[] { 20, 20 }, 0));

		Paint wayDefaultPaintOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
		wayDefaultPaintOutline.setStyle(Paint.Style.STROKE);
		wayDefaultPaintOutline.setColor(getResources().getColor(android.R.color.holo_blue_dark));
		wayDefaultPaintOutline.setAlpha(128);
		wayDefaultPaintOutline.setStrokeWidth(7);
		wayDefaultPaintOutline.setStrokeJoin(Paint.Join.ROUND);

		ArrayWayOverlay wayOverlay = new ArrayWayOverlay(wayDefaultPaintFill, wayDefaultPaintOutline);

		trackList.clear();

		GeoPoint startPoint = null;
		try {
			File gpxFile = new File(Environment.getExternalStorageDirectory(), gpxFileName);
			if (gpxFile.exists() && gpxFile.isFile()) {
				GPX gpx = new JDOM().parse(gpxFile);
				ArrayList<Track> tracks = gpx.getTracks();
				float length = 0;
				for (Track track : tracks) {
					ArrayList<TrackSegment> segments = track.getSegments();
					List<GeoPoint> points = new ArrayList<GeoPoint>();
					for (TrackSegment segment : segments) {
						ArrayList<Waypoint> waypoints = segment.getWaypoints();
						length += segment.length();
						for (Waypoint waypoint : waypoints) {
							Coordinate coordinate = waypoint.getCoordinate();
							GeoPoint geoPoint = new GeoPoint(coordinate.getLatitude(), coordinate.getLongitude());
							points.add(geoPoint);
							if (null == startPoint) {
								startPoint = geoPoint;
							}
						}
					}
					OverlayWay way1 = new OverlayWay(new GeoPoint[][] { points.toArray(new GeoPoint[points.size()]) });
					wayOverlay.addWay(way1);
					trackList.add(new TrackMetadata(gpxFileName, track.startingTime(), length));
				}
				textView.setText(String
						.format(getResources().getString(R.string.msg_track), gpxFileName, length / 1000));
			}
		} catch (ParsingException e) {
			if (!gpxFileName.isEmpty()) {
				Log.e(getClass().getCanonicalName(), String.format("Error parsing gpx file: %s", gpxFileName));
				Toast.makeText(getBaseContext(), String.format("Error parsing gpx file: %s", gpxFileName),
						Toast.LENGTH_LONG).show();
			}
		}

		mapView.getOverlays().clear();
		mapView.getOverlays().add(wayOverlay);
		if (null != startPoint) {
			mapView.setCenter(startPoint);
		}
	}
}