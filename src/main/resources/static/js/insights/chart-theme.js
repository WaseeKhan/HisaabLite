(function () {
    function isDarkTheme() {
        return document.documentElement.getAttribute('data-theme') === 'dark';
    }

    function getInsightsChartPalette() {
        return isDarkTheme()
            ? {
                axisColor: '#aeb9cb',
                textColor: '#f8fbff',
                gridColor: 'rgba(174, 185, 203, 0.14)',
                borderColor: 'rgba(255, 255, 255, 0.08)'
            }
            : {
                axisColor: '#667085',
                textColor: '#1f2a3d',
                gridColor: '#edf1f5',
                borderColor: '#dde5ef'
            };
    }

    function applyThemeToChart(chart) {
        if (!chart) return;

        var palette = getInsightsChartPalette();

        Chart.defaults.color = palette.axisColor;

        if (chart.options && chart.options.plugins && chart.options.plugins.legend && chart.options.plugins.legend.labels) {
            chart.options.plugins.legend.labels.color = palette.textColor;
        }

        var scaleKeys = Object.keys(chart.scales || {});
        scaleKeys.forEach(function (key) {
            var renderedScale = chart.scales[key];
            var scaleOptions = chart.options.scales && chart.options.scales[key];
            if (!scaleOptions) return;

            scaleOptions.ticks = scaleOptions.ticks || {};
            scaleOptions.grid = scaleOptions.grid || {};

            var isCategoryLike = renderedScale && (renderedScale.type === 'category' || renderedScale.type === 'time');
            scaleOptions.ticks.color = isCategoryLike ? palette.textColor : palette.axisColor;

            if (scaleOptions.grid.display !== false) {
                scaleOptions.grid.color = palette.gridColor;
                scaleOptions.border = scaleOptions.border || {};
                scaleOptions.border.color = palette.borderColor;
            }
        });

        chart.update();
    }

    function refreshInsightsCharts() {
        if (typeof Chart === 'undefined') return;

        Object.values(Chart.instances || {}).forEach(function (chart) {
            applyThemeToChart(chart);
        });
    }

    function buildInsightsUrl(basePath, params) {
        var url = new URL(basePath, window.location.origin);
        Object.keys(params || {}).forEach(function (key) {
            var value = params[key];
            if (value !== null && value !== undefined && value !== '') {
                url.searchParams.set(key, value);
            }
        });
        return url.toString();
    }

    function navigateInsightsUrl(basePath, params) {
        window.location.href = buildInsightsUrl(basePath, params);
    }

    window.getInsightsChartPalette = getInsightsChartPalette;
    window.refreshInsightsCharts = refreshInsightsCharts;
    window.buildInsightsUrl = buildInsightsUrl;
    window.navigateInsightsUrl = navigateInsightsUrl;

    document.addEventListener('DOMContentLoaded', function () {
        refreshInsightsCharts();
    });

    window.addEventListener('app-theme-change', function () {
        refreshInsightsCharts();
    });
})();
