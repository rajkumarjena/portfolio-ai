
1. The Best Professional Design Pattern: Layout Templating
   Instead of copying and pasting your header, sidebar, and footer into every HTML file, use a template engine. Since you are using Spring Boot, the universally accepted standard is Thymeleaf.
   • Create a Base Layout: Create one layout.html file that contains the outer structure: the <head> tags, the navigation bar, the sidebar, and the footer.
   • Inject Page Content: Use Thymeleaf's th:replace or th:insert fragments to dynamically inject only the specific page body (e.g., dashboard.html, settings.html) into the main layout container.

2. Centralized Stylesheet Strategy
To keep the design completely uniform, split your CSS into two levels:
• Global Stylesheet (global.css): Place this in your src/main/resources/static/css/ folder. Link it only once in your master layout file. It must contain:
• CSS Variables (:root) defining your exact brand colors, standard fonts, and margins.
• Typography rules (exact sizing for <h1>, <p>, etc.).
• Layout structural code (CSS Grid/Flexbox definitions for your header and sidebar).
• Component Stylesheet (components.css): A single global file defining uniform styles for buttons, input forms, tables, and alert cards.

3. Centralized & Module-Specific JavaScript Strategy
Organize your scripts logically inside your static/js/ directory to prevent code duplication and keep pages lightweight:
• Core App Script (app.js): Load this in the master layout. It handles site-wide functionality like opening/closing the sidebar mobile menu, managing user session timeouts, or handling dark mode toggles.
• Global HTTP Utility (api.js): A shared file containing configured fetch() requests or interceptors that talk to your Spring Boot REST APIs. This ensures uniform error handling and loading indicators globally.
• Module-Specific Scripts (/modules/dashboard.js, /modules/users.js): Write unique logic for specific pages in separate files. Link these scripts only inside the specific HTML fragments that need them, keeping them completely isolated.

4. Visual Standards for a "Professional" Look
• Stick to a Limited Palette: Use a maximum of three colors (one dominant neutral color like white/light gray, one primary brand color for buttons, and one subtle accent color).
• Adopt a Uniform Icon Set: Do not mix icon styles. Use a single CDN link (like Lucide Icons or FontAwesome) in your master layout so all icons share identical stroke weights.
• Whitespace is Key: Use consistent padding (e.g., exactly 16px or 24px) around containers and tables across every single page.
Would you like a minimal code example showing how to set up the Thymeleaf layout file along with the global.css variables?

5. Append logged-in user to every user page like it added in index.html page now at right top corner. Hence modified all html page and it's backend code in order to have logged in client at top right corner of every user interface in order to have same look and feel of all pages.
6. Currently the logged-in user(client id) is not mapped to FD or Bonds, make user is mandatory field in each investment type like fd, bond or mutual fund. its should be a mandatory field in their header table so that we can track overall user's portfolil investment and return based on any filter criteria like date range or between some date range what was the investment and return like of any user for dashboard reporting.