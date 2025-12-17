# How to Add Your Own Logo Image

## Quick Setup
1. **Find or create a recycling logo image** (PNG format recommended)
2. **Save it as `logo.png`** in the main project directory (same folder as `WMRTApp.java`)
3. **Run the application** - it will automatically use your image!

## Image Requirements
- **Format**: PNG, JPG, or GIF
- **Filename**: Must be exactly `logo.png`
- **Size**: Any size (the app will resize it to 120x120 pixels)
- **Content**: Any recycling-related image (recycling symbol, bin, eco-friendly design, etc.)

## Fallback
If no `logo.png` file is found, the app will automatically show a recycling symbol (♻) instead.

## Example Logo Ideas
- Universal recycling symbol (♻)
- Recycling bin with arrows
- Eco-friendly icon
- Your organization's logo
- Custom recycling-themed design

## File Location
```
wastemanagement/
├── logo.png          ← Put your logo here
├── src/
│   └── app/
│       └── WMRTApp.java
├── theme.css
└── ...
```

That's it! The app will automatically detect and use your logo image.

