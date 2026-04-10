function formatDateForInput(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function markActiveQuickFilter(type) {
    document.querySelectorAll('.quick-filter-chip').forEach((chip) => {
        const onclickValue = chip.getAttribute('onclick') || '';
        chip.classList.toggle('active', onclickValue.includes(`setQuickDateRange('${type}')`));
    });
}

function clearActiveQuickFilters() {
    document.querySelectorAll('.quick-filter-chip').forEach((chip) => {
        chip.classList.remove('active');
    });
}

function detectQuickRangeType() {
    const fromDate = document.getElementById('fromDate');
    const toDate = document.getElementById('toDate');

    if (!fromDate || !toDate || !fromDate.value || !toDate.value) return null;

    const today = new Date();
    const todayValue = formatDateForInput(today);

    const yesterday = new Date(today);
    yesterday.setDate(today.getDate() - 1);
    const yesterdayValue = formatDateForInput(yesterday);

    const thisWeekStart = new Date(today);
    const day = today.getDay();
    const diff = day === 0 ? 6 : day - 1;
    thisWeekStart.setDate(today.getDate() - diff);

    const thisMonthStart = new Date(today.getFullYear(), today.getMonth(), 1);
    const lastMonthStart = new Date(today.getFullYear(), today.getMonth() - 1, 1);
    const lastMonthEnd = new Date(today.getFullYear(), today.getMonth(), 0);

    if (fromDate.value === todayValue && toDate.value === todayValue) return 'today';
    if (fromDate.value === yesterdayValue && toDate.value === yesterdayValue) return 'yesterday';
    if (fromDate.value === formatDateForInput(thisWeekStart) && toDate.value === todayValue) return 'thisWeek';
    if (fromDate.value === formatDateForInput(thisMonthStart) && toDate.value === todayValue) return 'thisMonth';
    if (fromDate.value === formatDateForInput(lastMonthStart) && toDate.value === formatDateForInput(lastMonthEnd)) return 'lastMonth';

    return null;
}

function syncQuickFilterState() {
    const matchedType = detectQuickRangeType();
    if (matchedType) {
        markActiveQuickFilter(matchedType);
    } else {
        clearActiveQuickFilters();
    }
}

function setQuickDateRange(type) {
    const fromDate = document.getElementById('fromDate');
    const toDate = document.getElementById('toDate');

    if (!fromDate || !toDate) return;

    const today = new Date();
    let from = new Date();
    let to = new Date();

    switch (type) {
        case 'today':
            break;

        case 'yesterday':
            from.setDate(today.getDate() - 1);
            to.setDate(today.getDate() - 1);
            break;

        case 'thisWeek': {
            const day = today.getDay();
            const diff = day === 0 ? 6 : day - 1;
            from.setDate(today.getDate() - diff);
            break;
        }

        case 'thisMonth':
            from = new Date(today.getFullYear(), today.getMonth(), 1);
            break;

        case 'lastMonth':
            from = new Date(today.getFullYear(), today.getMonth() - 1, 1);
            to = new Date(today.getFullYear(), today.getMonth(), 0);
            break;

        default:
            return;
    }

    fromDate.value = formatDateForInput(from);
    toDate.value = formatDateForInput(to);
    markActiveQuickFilter(type);
}

function resetInsightsFilters() {
    const form = document.querySelector('.insights-filter-form');
    if (!form) return;

    form.querySelectorAll('input[type="date"], input[type="text"], select').forEach(field => {
        field.value = '';
    });

    clearActiveQuickFilters();
}

function printInsightsReport() {
    window.print();
}

document.addEventListener('DOMContentLoaded', () => {
    syncQuickFilterState();

    const fromDate = document.getElementById('fromDate');
    const toDate = document.getElementById('toDate');

    [fromDate, toDate].forEach((field) => {
        if (!field) return;
        field.addEventListener('change', syncQuickFilterState);
    });
});
