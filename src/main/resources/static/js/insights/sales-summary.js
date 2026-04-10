const primaryBlue = '#2f6fe4';
const primaryBlueSoft = 'rgba(47, 111, 228, 0.16)';
const accentOrange = '#f39a2d';
const mutedSlate = '#8a94a6';
function getChartThemePalette() {
    if (window.getInsightsChartPalette) {
        return window.getInsightsChartPalette();
    }
    return {
        axisColor: '#667085',
        textColor: '#1f2a3d',
        gridColor: 'rgba(148, 163, 184, 0.16)'
    };
}

const gridColor = getChartThemePalette().gridColor;
const labelColor = getChartThemePalette().axisColor;

Chart.defaults.font.family = "'Manrope', 'Inter', sans-serif";
Chart.defaults.color = labelColor;

function buildLineChart(ctx, labels, values) {
    if (!ctx) return;

    new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'Sales',
                    data: values,
                    borderColor: primaryBlue,
                    backgroundColor: primaryBlueSoft,
                    fill: true,
                    tension: 0.38,
                    pointRadius: 4,
                    pointHoverRadius: 5,
                    pointBackgroundColor: primaryBlue,
                    borderWidth: 3
                }
            ]
        },
        options: {
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: false
                }
            },
            onClick: function (event, elements, chart) {
                if (!elements || !elements.length || !window.navigateInsightsUrl) return;
                var label = chart.data.labels[elements[0].index];
                window.navigateInsightsUrl('/sales/history', {
                    status: String(label || '').toUpperCase()
                });
            },
            scales: {
                x: {
                    grid: {
                        display: false
                    },
                    ticks: {
                        color: getChartThemePalette().textColor
                    }
                },
                y: {
                    beginAtZero: true,
                    grid: {
                        color: gridColor
                    },
                    ticks: {
                        color: getChartThemePalette().axisColor,
                        callback: function (value) {
                            return '₹' + value;
                        }
                    }
                }
            }
        }
    });
}

function initSalesTrendChart(labels, values) {
    const salesTrendCtx = document.getElementById('salesTrendChart');
    if (!salesTrendCtx) return;

    buildLineChart(salesTrendCtx, labels, values);
}

document.addEventListener('DOMContentLoaded', function () {
    const salesTrendCtx = document.getElementById('salesTrendChart');

    // fallback static chart only if no server-side data function call happened
    if (salesTrendCtx && !window.__salesTrendInitialized) {
        buildLineChart(
            salesTrendCtx,
            ['1 Apr', '2 Apr', '3 Apr', '4 Apr', '5 Apr', '6 Apr', '7 Apr'],
            [6200, 7100, 6850, 8200, 7900, 9050, 8450]
        );
    }

    // IMPORTANT: use the same id that exists in your HTML
    const paymentModeCtx = document.getElementById('paymentModeChart');
    if (paymentModeCtx && !window.__paymentModeInitialized) {
        initPaymentModeChart(
            ['UPI', 'Cash', 'Card', 'Mixed'],
            [42, 31, 18, 9]
        );
    }

    const topProductsCtx = document.getElementById('topProductsChart');
    if (topProductsCtx && !window.__topProductsInitialized) {
        initTopProductsChart(
            ['Paracetamol', 'Azithromycin', 'Cetirizine', 'Pantoprazole', 'Vitamin C'],
            [12500, 9800, 8300, 7600, 6900]
        );
    }

    const billingPerformanceCtx = document.getElementById('billingPerformanceChart');
    if (billingPerformanceCtx) {
        new Chart(billingPerformanceCtx, {
            type: 'bar',
            data: {
                labels: ['Completed', 'Cancelled'],
                datasets: [
                    {
                        label: 'Bills',
                        data: [144, 4],
                        backgroundColor: [primaryBlue, accentOrange],
                        borderRadius: 12,
                        maxBarThickness: 52
                    }
                ]
            },
            options: {
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    }
                },
                scales: {
                    x: {
                        grid: {
                            display: false
                        },
                        ticks: {
                            color: getChartThemePalette().textColor
                        }
                    },
                    y: {
                        beginAtZero: true,
                        grid: {
                            color: getChartThemePalette().gridColor
                        },
                        ticks: {
                            color: getChartThemePalette().axisColor
                        }
                    }
                }
            }
        });
    }
});



function initPaymentModeChart(labels, values) {
    const paymentModeCtx = document.getElementById('paymentModeChart');
    if (!paymentModeCtx) return;

    new Chart(paymentModeCtx, {
        type: 'doughnut',
        data: {
            labels: labels,
            datasets: [{
                data: values,
                backgroundColor: [
                    '#2f6fe4',
                    '#f39a2d',
                    '#7c8db5',
                    '#d9e1f2',
                    '#202939',
                    '#91aff1'
                ],
                borderWidth: 0,
                hoverOffset: 6
            }]
        },
        options: {
            maintainAspectRatio: false,
            cutout: '68%',
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: {
                        color: getChartThemePalette().textColor,
                        font: {
                            family: 'Manrope',
                            weight: '700'
                        },
                        boxWidth: 12,
                        usePointStyle: true,
                        pointStyle: 'circle',
                        padding: 16
                    }
                }
            },
            onClick: function (event, elements, chart) {
                if (!elements || !elements.length || !window.navigateInsightsUrl) return;
                var label = chart.data.labels[elements[0].index];
                window.navigateInsightsUrl('/insights/sales/invoices', {
                    fromDate: document.getElementById('fromDate') ? document.getElementById('fromDate').value : '',
                    toDate: document.getElementById('toDate') ? document.getElementById('toDate').value : '',
                    paymentMode: String(label || '').toUpperCase(),
                    keyword: document.getElementById('keyword') ? document.getElementById('keyword').value : ''
                });
            }
        }
    });
}




function initTopProductsChart(labels, values) {
    const topProductsCtx = document.getElementById('topProductsChart');
    if (!topProductsCtx) return;

    new Chart(topProductsCtx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'Sales Value',
                data: values,
                backgroundColor: ['#2f6fe4', '#4d82e8', '#6f99ed', '#91aff1', '#f39a2d'],
                borderRadius: 10,
                borderSkipped: false
            }]
        },
        options: {
            indexAxis: 'y',
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            onClick: function (event, elements, chart) {
                if (!elements || !elements.length || !window.navigateInsightsUrl) return;
                var label = chart.data.labels[elements[0].index];
                window.navigateInsightsUrl('/products', { keyword: label });
            },
            scales: {
                x: {
                    grid: { color: getChartThemePalette().gridColor },
                    ticks: {
                        color: getChartThemePalette().axisColor,
                        callback: function (value) {
                            return '₹' + value;
                        }
                    }
                },
                y: {
                    grid: { display: false },
                    ticks: { color: getChartThemePalette().textColor }
                }
            }
        }
    });
}
