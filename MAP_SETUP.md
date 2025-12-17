# Setup Instructions for Map Integration

## What Was Added

I've integrated a real-world interactive map into your waste management app using **OpenStreetMap** and **Leaflet.js**. The map now displays:

- 🗺️ **Interactive Bacolod City map** with real geography
- 📍 **Collection center markers** color-coded by status (Green=Open, Orange=Busy, Red=Closed)
- 🏠 **Depot marker** at Bacolod City Hall area
- 🛣️ **Route visualization** showing the Dijkstra-optimized collection path
- 📊 **Info panel** with distance, time, and center count
- 🔍 **Interactive features**: zoom, pan, click markers for details

---

## Files Modified/Created

### Created:
1. **`map-viewer.html`** - Interactive map viewer with Leaflet.js

### Modified:
1. **`WMRTApp.java`** - Replaced Canvas with WebView in `buildCollectionRouteTab()`
2. **`module-info.java`** - Added `requires javafx.web;`

---

## Setup Steps

### 1. Verify JavaFX Web Module

The app now requires the **JavaFX Web** module for WebView support. Check your current setup:

**Current batch file location:** `runJavaFX.bat`

Open your `runJavaFX.bat` and ensure you're using the **fullJavaFX SDK** (not just controls). It should look like:

```batch
@echo off
set PATH_TO_FX="C:\path\to\javafx-sdk-24.0.2\lib"
javac --module-path %PATH_TO_FX% --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.web -d out src\module-info.java src\app\*.java
java --module-path %PATH_TO_FX% --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.web -cp out app.WMRTApp
```

**Important:** Notice the `,javafx.web` added to `--add-modules`

### 2. Update Your Build/Run Script

If using your existing `runJavaFX.bat`, update it to include `javafx.web`:

**Before:**
```
--add-modules javafx.controls,javafx.fxml,javafx.graphics
```

**After:**
```
--add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.web
```

### 3. Verify File Locations

Ensure these files are in your project root directory:
- ✅ `map-viewer.html` (newly created)
- ✅ `users.csv`
- ✅ `approved.csv`
- ✅ `pending.csv`
- ✅ `theme.css`
- ✅ `theme-override.css`

### 4. Run the Application

```batch
.\runJavaFX.bat
```

Or if compiling manually:

```batch
# Compile
javac --module-path "path\to\javafx-sdk-24.0.2\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.web -d out src\module-info.java src\app\*.java

# Run
java --module-path "path\to\javafx-sdk-24.0.2\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.web -cp out app.WMRTApp
```

---

## Testing the Map

1. **Login** to the app (use any existing user)
2. Navigate to the **"Collection Route"** tab
3. You should see:
   - Interactive map centered on Bacolod City
   - 5 collection center markers (color-coded)
   - Blue route line connecting all centers
   - Info panel showing distance and time
   - Route statistics below the map

### Map Features:
- **Zoom**: Mouse wheel or +/- buttons
- **Pan**: Click and drag
- **Marker Info**: Click any marker to see center details
- **Legend**: Bottom right shows status colors

---

## Troubleshooting

### Issue: "javafx.web cannot be resolved to a module"

**Solution:** Your JavaFX SDK doesn't include the web module. You need the **full JavaFX SDK**:

1. Download from: https://gluonhq.com/products/javafx/
2. Choose version 24.0.2 (or latest)
3. Download the **complete SDK** (not just controls)
4. Update `PATH_TO_FX` in your batch file

### Issue: Map shows blank/white screen

**Solution:** 
1. Verify `map-viewer.html` is in the project root (same folder as `users.csv`)
2. Check browser console in WebView (if accessible)
3. Ensure internet connection (Leaflet loads map tiles from OpenStreetMap)

### Issue: Markers don't appear

**Solution:**
- The coordinates in the Store class are using the actual Bacolod City coordinates
- If markers are off-map, the coordinates might need adjustment
- Check console output for JavaScript errors

---

## Next Steps (Optional Enhancements)

1. **Real Coordinates**: Update the collection center coordinates in the `Store` class to actual barangay locations in Bacolod
2. **Road Routing**: Integrate GraphHopper or OSRM API for real road-based routing instead of straight-line Dijkstra
3. **User Location**: Add ability to show user's current location on map
4. **Route Animation**: Animate the route drawing on map load

---

## Technical Details

- **Map Library**: Leaflet.js 1.9.4 (loaded from CDN)
- **Map Tiles**: OpenStreetMap (free, no API key needed)
- **Communication**: Java ↔ JavaScript bridge via `WebEngine.executeScript()`
- **Default Center**: Bacolod City Hall (10.6762, 122.9501)
- **Zoom Level**: 13 (city-wide view)

---

## Support

If you encounter any issues:
1. Check that all files are in the correct location
2. Verify JavaFX SDK includes the web module
3. Ensure internet connection for map tiles
4. Check console output for error messages

The map integration is complete and ready to use! 🎉
