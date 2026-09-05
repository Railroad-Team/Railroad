(() => {
    const search = document.getElementById('search');
    const incomplete = document.getElementById('incomplete');
    const packages = [...document.querySelectorAll('details.package')];
    const types = [...document.querySelectorAll('details.type')];
    const rows = [...document.querySelectorAll('tr[data-search]')];
    function filter() {
        const query = search.value.trim().toLowerCase();
        let visible = 0;
        for (const row of rows) {
            row.hidden = !row.dataset.search.includes(query) || (incomplete.checked && row.dataset.complete === 'true');
            if (!row.hidden) visible++;
        }
        for (const type of types) {
            type.hidden = ![...type.querySelectorAll('tbody tr')].some(row => !row.hidden);
            if (query && !type.hidden) type.open = true;
        }
        for (const pkg of packages) {
            pkg.hidden = ![...pkg.querySelectorAll('.type')].some(type => !type.hidden);
            if ((query || incomplete.checked) && !pkg.hidden) pkg.open = true;
        }
        document.getElementById('no-results').hidden = visible > 0;
        document.getElementById('visible-count').textContent = `${visible.toLocaleString()} declarations shown`;
    }
    function revealHash() {
        const target = document.getElementById(location.hash.slice(1));
        if (!target) return;
        if (target.closest('[hidden]')) {
            search.value = '';
            incomplete.checked = false;
            filter();
        }
        for (let node = target; node; node = node.parentElement) {
            if (node.tagName === 'DETAILS') node.open = true;
        }
        target.scrollIntoView({ block: 'start' });
    }
    search.addEventListener('input', filter);
    incomplete.addEventListener('change', filter);
    for (const action of ['expand', 'collapse']) {
        document.getElementById(action).addEventListener('click', () => {
            for (const section of [...packages, ...types]) {
                if (!section.hidden) section.open = action === 'expand';
            }
        });
    }
    window.addEventListener('hashchange', revealHash);
    // Clicking the current anchor again must still reveal a subsequently collapsed section.
    document.addEventListener('click', event => {
        const link = event.target.closest('a[href^="#"]');
        if (link && link.hash === location.hash) revealHash();
    });
    filter();
    revealHash();
})();
