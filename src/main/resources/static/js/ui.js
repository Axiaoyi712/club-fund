(() => {
    const sidebar = document.querySelector('#sidebar');
    const menu = document.querySelector('.mobile-menu');
    const scrim = document.querySelector('.scrim');
    const mobile = window.matchMedia('(max-width: 767px)');
    if (sidebar && menu) {
        function setMenu(open, restoreFocus = false) {
            sidebar.classList.toggle('open', open);
            menu.setAttribute('aria-expanded', String(open));
            scrim.hidden = !open;
            sidebar.inert = mobile.matches && !open;
            document.body.style.overflow = open && mobile.matches ? 'hidden' : '';
            if (open) sidebar.querySelector('.sidebar-close').focus();
            if (restoreFocus) menu.focus();
        }
        menu.addEventListener('click', () => setMenu(menu.getAttribute('aria-expanded') !== 'true'));
        sidebar.querySelector('.sidebar-close').addEventListener('click', () => setMenu(false, true));
        scrim.addEventListener('click', () => setMenu(false, true));
        mobile.addEventListener('change', () => setMenu(false));
        document.addEventListener('keydown', event => {
            if (!sidebar.classList.contains('open')) return;
            if (event.key === 'Escape') setMenu(false, true);
            if (event.key === 'Tab') {
                const controls = [...sidebar.querySelectorAll('a[href],button')].filter(element => element.getClientRects().length);
                const first = controls[0], last = controls[controls.length - 1];
                if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
                if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
            }
        });
        setMenu(false);
    }
    document.addEventListener('submit', event => {
        if (event.defaultPrevented) return;
        event.target.setAttribute('aria-busy', 'true');
        if (event.submitter) event.submitter.classList.add('is-submitting');
    });
    window.addEventListener('pageshow', () => {
        document.querySelectorAll('[aria-busy]').forEach(form => form.removeAttribute('aria-busy'));
        document.querySelectorAll('.is-submitting').forEach(button => button.classList.remove('is-submitting'));
    });
})();
