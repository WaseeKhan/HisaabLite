document.addEventListener('DOMContentLoaded', function () {
    const overlay = document.getElementById('shopShortcutOverlay');
    const hintButton = document.getElementById('shopShortcutHint');
    const closeButtons = document.querySelectorAll('[data-close-shortcuts]');
    const shortcutMap = {
        d: '/dashboard',
        b: '/sales/new',
        p: '/products',
        h: '/sales/history',
        s: '/staff',
        r: '/profile',
        a: '/activity',
        u: '/support'
    };

    if (!overlay) {
        return;
    }

    function isOverlayOpen() {
        return overlay.classList.contains('show');
    }

    function openOverlay() {
        overlay.classList.add('show');
        overlay.setAttribute('aria-hidden', 'false');
        const closeButton = overlay.querySelector('.shop-shortcut-close');
        closeButton?.focus();
    }

    function closeOverlay() {
        overlay.classList.remove('show');
        overlay.setAttribute('aria-hidden', 'true');
        hintButton?.focus();
    }

    function findNavLink(path) {
        return Array.from(document.querySelectorAll('.shop-nav-link'))
            .find(function (link) {
                try {
                    return new URL(link.href, window.location.origin).pathname === path;
                } catch (error) {
                    return false;
                }
            });
    }

    function navigateTo(path) {
        const navLink = findNavLink(path);
        if (!navLink) {
            return;
        }

        window.location.href = navLink.getAttribute('href');
    }

    hintButton?.addEventListener('click', openOverlay);

    closeButtons.forEach(function (button) {
        button.addEventListener('click', closeOverlay);
    });

    document.addEventListener('keydown', function (event) {
        const key = event.key.toLowerCase();

        if (event.key === 'F1') {
            event.preventDefault();
            if (isOverlayOpen()) {
                closeOverlay();
            } else {
                openOverlay();
            }
            return;
        }

        if (event.key === 'Escape' && isOverlayOpen()) {
            event.preventDefault();
            closeOverlay();
            return;
        }

        const useAltShortcut = event.altKey && !event.ctrlKey && !event.metaKey;
        const useMacCtrlShortcut = event.ctrlKey && !event.altKey && !event.metaKey;

        if (!useAltShortcut && !useMacCtrlShortcut) {
            return;
        }

        const path = shortcutMap[key];
        if (!path) {
            return;
        }

        event.preventDefault();
        if (isOverlayOpen()) {
            closeOverlay();
        }
        navigateTo(path);
    });
});
