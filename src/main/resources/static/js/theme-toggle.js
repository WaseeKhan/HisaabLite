(function () {
    var STORAGE_KEY = 'rxarogya-theme';
    var root = document.documentElement;
    var mediaQuery = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

    function preferredTheme() {
        var stored = null;
        try {
            stored = window.localStorage.getItem(STORAGE_KEY);
        } catch (error) {
            stored = null;
        }

        if (stored === 'light' || stored === 'dark') {
            return stored;
        }

        return mediaQuery && mediaQuery.matches ? 'dark' : 'light';
    }

    function applyTheme(theme) {
        root.setAttribute('data-theme', theme);
        root.style.colorScheme = theme;

        document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
            button.setAttribute('aria-label', theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
            button.setAttribute('title', theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
            button.setAttribute('data-current-theme', theme);
        });

        document.querySelectorAll('[data-theme-icon]').forEach(function (icon) {
            icon.className = theme === 'dark' ? 'fas fa-sun' : 'fas fa-moon';
        });

        document.querySelectorAll('[data-theme-label]').forEach(function (label) {
            var compact = label.getAttribute('data-theme-label-mode') === 'compact';
            label.textContent = theme === 'dark'
                ? (compact ? 'Light' : 'Light Mode')
                : (compact ? 'Dark' : 'Dark Mode');
        });
    }

    function persistTheme(theme) {
        try {
            window.localStorage.setItem(STORAGE_KEY, theme);
        } catch (error) {
            // Ignore storage failures and still apply the theme.
        }
        applyTheme(theme);
    }

    window.toggleAppTheme = function () {
        var nextTheme = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        persistTheme(nextTheme);
    };

    function initTheme() {
        applyTheme(preferredTheme());
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initTheme);
    } else {
        initTheme();
    }

    if (mediaQuery && typeof mediaQuery.addEventListener === 'function') {
        mediaQuery.addEventListener('change', function (event) {
            var stored = null;
            try {
                stored = window.localStorage.getItem(STORAGE_KEY);
            } catch (error) {
                stored = null;
            }

            if (stored !== 'light' && stored !== 'dark') {
                applyTheme(event.matches ? 'dark' : 'light');
            }
        });
    }
})();
