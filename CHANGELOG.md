## 0.2.1

- Fix plugin icon by replacing SVG with base64 encoded image for better compatibility
- Pin runPluginVerifier IDE versions to avoid CDN download failures

## 0.2.0

- Add tool window with re-entry briefing display and session context
- Add status bar widget showing last activity time
- Add data service for reading `.keepgoing/` session and decision data
- Add briefing generator for synthesizing re-entry summaries
- Add setup notification when `.keepgoing/` data is missing
- Improve tool window UI with monochrome icon, text wrapping, and modern layout
- Add Marketplace publishing config and CI release workflow

## 0.1.0

- Initial release
- Re-entry briefing notification on project open after 3+ days of inactivity
- Sidebar panel with briefing, session history, and decisions
- Status bar indicator showing last activity time
- Open touched files action to restore workspace context
