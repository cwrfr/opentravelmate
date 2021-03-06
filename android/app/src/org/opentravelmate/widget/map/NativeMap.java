package org.opentravelmate.widget.map;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opentravelmate.commons.ExceptionListener;
import org.opentravelmate.commons.UIThreadExecutor;
import org.opentravelmate.widget.HtmlLayout;
import org.opentravelmate.widget.HtmlLayoutParams;

import android.annotation.SuppressLint;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.SparseArray;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.RelativeLayout;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.gms.maps.model.UrlTileProvider;

/**
 * Injected object.
 * 
 * @author marc.plouhinec@gmail.com (Marc Plouhinec)
 */
public class NativeMap {
	
	public static final String GLOBAL_OBJECT_NAME = "org_opentravelmate_native_widget_map_nativeMap";
	public static final String SCRIPT_URL = "/native/widget/map/nativeMap.js";
	private static final float DEFAULT_ZOOM = 13;
	private static final double DEFAULT_LATITUDE = 49.6;
	private static final double DEFAULT_LONGITUDE = 6.135;
	
	private final ExceptionListener exceptionListener;
	private final HtmlLayout htmlLayout;
	private final FragmentManager fragmentManager;
	private final Map<String, GoogleMap> mapByPlaceHolderId = new ConcurrentHashMap<String, GoogleMap>();
	private final SparseArray<com.google.android.gms.maps.model.Marker> gmarkerById =
			new SparseArray<com.google.android.gms.maps.model.Marker>();
	private final Map<String, TileObserver> tileObserverByPlaceHolderId = new HashMap<String, TileObserver>();
	
	/**
	 * Create a NativeMap object.
	 */
	public NativeMap(ExceptionListener exceptionListener, HtmlLayout htmlLayout, FragmentManager fragmentManager) {
		this.exceptionListener = exceptionListener;
		this.htmlLayout = htmlLayout;
		this.fragmentManager = fragmentManager;
	}

	/**
	 * Build the native view object for the current widget.
	 * 
	 * @param jsonLayoutParams
	 */
	@JavascriptInterface
	public void buildView(final String jsonLayoutParams) {
		UIThreadExecutor.execute(new Runnable() {
			@Override public void run() {
				try {
					buildView(HtmlLayoutParams.fromJsonLayoutParams(new JSONObject(jsonLayoutParams)));
				} catch (JSONException e) {
					exceptionListener.onException(false, e);
				}
			}
		});
	}
	
	/**
	 * Update the native view object for the current widget.
	 * 
	 * @param jsonLayoutParams
	 */
	@JavascriptInterface
	public void updateView(final String jsonLayoutParams) {
		UIThreadExecutor.execute(new Runnable() {
			@Override public void run() {
				try {
					HtmlLayoutParams layoutParams = HtmlLayoutParams.fromJsonLayoutParams(new JSONObject(jsonLayoutParams));
					View view = htmlLayout.findViewByPlaceHolderId(layoutParams.id);
					if (view != null) {
						view.setLayoutParams(layoutParams);
					}
				} catch (JSONException e) {
					exceptionListener.onException(false, e);
				}
			}
		});
	}
	
	/**
	 * Remove the native view object for the current widget.
	 * 
	 * @param id Place holder ID
	 */
	@JavascriptInterface
	public void removeView(final String id) {
		UIThreadExecutor.execute(new Runnable() {
			@Override public void run() {
				View view = htmlLayout.findViewByPlaceHolderId(id);
				if (view != null) {
					htmlLayout.removeView(view);
				}
			}
		});
	}
	
	/**
	 * Build the native view object for the current widget.
	 * 
	 * @param layoutParams
	 */
	@SuppressLint("SetJavaScriptEnabled")
	public void buildView(final HtmlLayoutParams layoutParams) {
		CameraPosition cameraPosition = new CameraPosition.Builder()
			.target(new com.google.android.gms.maps.model.LatLng(DEFAULT_LATITUDE, DEFAULT_LONGITUDE))
			.zoom(DEFAULT_ZOOM)
			.build();
		GoogleMapOptions options = new GoogleMapOptions().camera(cameraPosition);
		GoogleMapFragment mapFragment = GoogleMapFragment.newInstance(options);
		mapFragment.setOnGoogleMapFragmentListener(new GoogleMapFragment.OnGoogleMapFragmentListener() {
			@Override public void onMapReady(GoogleMap map) {
				mapByPlaceHolderId.put(layoutParams.id, map);
			}
		});
		
		RelativeLayout mapLayout = new RelativeLayout(htmlLayout.getContext());
		mapLayout.setLayoutParams(layoutParams);
		htmlLayout.addView(mapLayout);
		
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		fragmentTransaction.add(mapLayout.getId(), mapFragment);
		fragmentTransaction.addToBackStack(null);
		fragmentTransaction.commit();
	}
	
	/**
	 * Add an overlay to the map.
	 * 
	 * @param id
     *     Map place holder ID.
	 * @param jsonTileOverlay
	 *     JSON serialized TileOverlay.
	 */
	@JavascriptInterface
	public void addTileOverlay(final String id, final String jsonTileOverlay) {
		final GoogleMap map = getGoogleMapSync(id);
		
		UIThreadExecutor.execute(new Runnable() {
			@Override public void run() {
				try {
					TileOverlay tileOverlay = TileOverlay.fromJsonTileOverlay(new JSONObject(jsonTileOverlay));
					
					TileProvider tileProvider = new UrlPatternTileProvider(tileOverlay.tileUrlPattern);
					map.addTileOverlay(new TileOverlayOptions()
						.tileProvider(tileProvider)
						.zIndex(tileOverlay.zIndex));
				} catch (JSONException e) {
					exceptionListener.onException(false, e);
				}
			}
		});
	}
	
	/**
	 * Move the map center to the given location.
	 * 
	 * @param id
	 *     Map place holder ID.
	 * @param jsonCenter
	 *     JSON serialized LatLng.
	 */
	@JavascriptInterface
	public void panTo(final String id, final String jsonCenter) {
		final GoogleMap map = getGoogleMapSync(id);
		
		UIThreadExecutor.execute(new Runnable() {
			@Override public void run() {
				try {
					LatLng center = LatLng.fromJsonLatLng(new JSONObject(jsonCenter));
					CameraPosition cameraPosition = new CameraPosition.Builder()
						.target(new com.google.android.gms.maps.model.LatLng(center.lat, center.lng))
						.zoom(map.getCameraPosition().zoom)
						.build();
					map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
				} catch (JSONException e) {
					exceptionListener.onException(false, e);
				}
			}
		});
	}
	
	/**
	 * Add a marker on the map.
	 * 
	 * @param id
	 *     Map place holder ID.
	 * @param jsonMarker
     *     JSON serialized Marker.
	 */
	@JavascriptInterface
	public void addMarker(final String id, final String jsonMarker) {
		final GoogleMap map = getGoogleMapSync(id);
		
		UIThreadExecutor.execute(new Runnable() {
			@Override public void run() {
				try {
					Marker marker = Marker.fromJsonMarker(new JSONObject(jsonMarker));
					MarkerOptions markerOptions = new MarkerOptions()
						.position(new com.google.android.gms.maps.model.LatLng(marker.position.lat, marker.position.lng))
						.title(marker.title); 
					if (marker.anchorPoint != null) {
						markerOptions.anchor((float)marker.anchorPoint.x, (float)marker.anchorPoint.y);
					}
					
					gmarkerById.put(marker.id, map.addMarker(markerOptions));
				} catch (JSONException e) {
					exceptionListener.onException(false, e);
				}
			}
		});
	}
	
	/**
	 * Remove a marker from the map.
	 * 
	 * @param id
	 *     Map place holder ID.
	 * @param jsonMarker
     *     JSON serialized Marker.
	 */
	@JavascriptInterface
	public void removeMarker(final String id, final String jsonMarker) {
		UIThreadExecutor.execute(new Runnable() {
			@Override public void run() {
				try {
					Marker marker = Marker.fromJsonMarker(new JSONObject(jsonMarker));
					gmarkerById.get(marker.id).remove();
					gmarkerById.remove(marker.id);
				} catch (JSONException e) {
					exceptionListener.onException(false, e);
				}
			}
		});
	}
	
	/**
	 * Start observing tiles and forward the TILES_DISPLAYED and TILES_RELEASED events to the
     * map defined by the given place-holder ID.
     * Note: this function does nothing if the tiles are already observed.
     * 
	 * @param id
	 *     Map place holder ID.
	 */
	@JavascriptInterface
	public void observeTiles(final String id) {
		final GoogleMap map = getGoogleMapSync(id);
		
		UIThreadExecutor.execute(new Runnable() {
			@Override public void run() {
				TileObserver tileObserver = tileObserverByPlaceHolderId.get(id);
				
				if (tileObserver == null) {
					tileObserver = new TileObserver(map, htmlLayout.findViewByPlaceHolderId(id));
					tileObserverByPlaceHolderId.put(id, tileObserver);
					
					tileObserver.onTilesDisplayed(new TileObserver.TilesListener() {
						@Override public void on(List<TileCoordinates> tileCoordinates) {
							WebView mainWebView = (WebView)htmlLayout.findViewByPlaceHolderId(HtmlLayout.MAIN_WEBVIEW_ID);
							try {
								JSONArray jsonTileCoordinates = TileCoordinates.toJson(tileCoordinates);
								mainWebView.loadUrl("javascript:(function(){" +
										"    require(['extensions/core/widget/Widget'], function (Widget) {" +
										"        var map = Widget.findById('" + id + "');" +
										"         map.fireTileEvent('TILES_DISPLAYED', " + jsonTileCoordinates.toString(2) + ");" +
										"    });" +
										"})();");
							} catch(JSONException e) {
								exceptionListener.onException(false, e);
							}
						}
					});
					tileObserver.onTilesReleased(new TileObserver.TilesListener() {
						@Override public void on(List<TileCoordinates> tileCoordinates) {
							WebView mainWebView = (WebView)htmlLayout.findViewByPlaceHolderId(HtmlLayout.MAIN_WEBVIEW_ID);
							try {
								JSONArray jsonTileCoordinates = TileCoordinates.toJson(tileCoordinates);
								mainWebView.loadUrl("javascript:(function(){" +
										"    require(['extensions/core/widget/Widget'], function (Widget) {" +
										"        var map = Widget.findById('" + id + "');" +
										"         map.fireTileEvent('TILES_RELEASED', " + jsonTileCoordinates.toString(2) + ");" +
										"    });" +
										"})();");
							} catch(JSONException e) {
								exceptionListener.onException(false, e);
							}
						}
					});
				}
			}
		});
	}
	
	/**
	 * Get all the visible tile coordinates.
     * Note: the function observeTiles() must be called before executing this one.
     * 
     * @param id
	 *     Map place holder ID.
	 * @return JSON-serialized Array.<{zoom: Number, x: Number, y: Number}>
	 */
	@JavascriptInterface
	public String getDisplayedTileCoordinates(final String id) {
		try {
			JSONArray jsonDisplayedTileCoordinates = UIThreadExecutor.executeSync(new Callable<JSONArray>() {
				@Override public JSONArray call() throws Exception {
					TileObserver tileObserver = tileObserverByPlaceHolderId.get(id);
					if (tileObserver != null) {
						List<TileCoordinates> displayedTileCoordinates = tileObserver.getDisplayedTileCoordinates();
						return TileCoordinates.toJson(displayedTileCoordinates);
					}
					return new JSONArray();
				}
			}, 100);
			return jsonDisplayedTileCoordinates.toString(2);
		} catch (Exception e) {
			exceptionListener.onException(false, e);
		}
		
		return "[]";
	}
	
	/**
	 * Get the map. Wait if the map is not ready.
	 * 
	 * @param id
	 *     Map place holder ID.
	 * @return map
	 */
	private GoogleMap getGoogleMapSync(String id) {
		long watchDog = 100;
		GoogleMap googleMap = null;
		while (googleMap == null && watchDog-- > 0) {
			googleMap = mapByPlaceHolderId.get(id);
			try { Thread.sleep(100); } catch (InterruptedException e) { }
		}
		return googleMap;
	}
	
	/**
	 * TileProvider based on TileOverlay.tileUrlPattern.
	 */
	private class UrlPatternTileProvider extends UrlTileProvider {
		
		private final String tileUrlPattern;

		/**
		 * Create a new UrlPatternTileProvider.
		 * 
		 * @param tileUrlPattern
		 */
		public UrlPatternTileProvider(String tileUrlPattern) {
			super(256, 256);
			this.tileUrlPattern = tileUrlPattern;
		}

		@Override
		public URL getTileUrl(final int x, final int y, final int zoom) {
			String url = tileUrlPattern
					.replace("${zoom}", String.valueOf(zoom))
					.replace("${x}", String.valueOf(x))
					.replace("${y}", String.valueOf(y));
			try {
				return new URL(url);
			} catch (MalformedURLException e) {
				exceptionListener.onException(false, e);
				return null;
			}
		}
	}
}
