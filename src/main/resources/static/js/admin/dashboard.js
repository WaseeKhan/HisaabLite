window.initAdminDashboardCharts = function initAdminDashboardCharts(config) {
    const revenueLabels = config.revenueLabels || [];
    const revenueData = config.revenueData || [];
    const planLabels = config.planLabels || ["FREE", "BASIC", "PRO"];
    const planData = config.planData || [0, 0, 0];
    const usersData = config.usersData || [];
    const ticketLabels = config.ticketLabels || revenueLabels;
    const ticketData = config.ticketData || [0, 0, 0, 0, 0, 0, 0];
    const totalRevenue = Number(config.totalRevenue || 0);
    const totalShops = Number(config.totalShops || 0);

    const chartFont = {
        family: "Manrope",
        size: 11
    };

    const axisColor = "rgba(166, 181, 210, 0.72)";
    const gridColor = "rgba(115, 134, 172, 0.16)";
    const tooltipBackground = "rgba(6, 12, 24, 0.96)";
    const tooltipBorder = "rgba(139, 92, 246, 0.18)";

    const baseAxis = {
        grid: { color: gridColor, lineWidth: 1 },
        border: { color: "rgba(115, 134, 172, 0.12)" },
        ticks: {
            color: axisColor,
            font: chartFont
        }
    };

    const commonPlugins = {
        legend: { display: false },
        tooltip: {
            backgroundColor: tooltipBackground,
            borderColor: tooltipBorder,
            borderWidth: 1,
            titleColor: "#eef4ff",
            bodyColor: "#d6e0f3",
            titleFont: { family: "Manrope", size: 11, weight: "700" },
            bodyFont: { family: "Manrope", size: 11 }
        }
    };

    const donutCenterTextPlugin = {
        id: "donutCenterTextPlugin",
        beforeDraw(chart, args, options) {
            if (!options || !options.textTop) {
                return;
            }

            const meta = chart.getDatasetMeta(0);
            if (!meta || !meta.data || !meta.data.length) {
                return;
            }

            const { ctx } = chart;
            const x = meta.data[0].x;
            const y = meta.data[0].y;

            ctx.save();
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillStyle = "#eef4ff";
            ctx.font = "800 16px Manrope";
            ctx.fillText(options.textTop, x, y - 10);
            ctx.fillStyle = "rgba(166, 181, 210, 0.8)";
            ctx.font = "600 11px Manrope";
            ctx.fillText(options.textBottom || "", x, y + 12);
            ctx.restore();
        }
    };

    if (!Chart.registry.plugins.get("donutCenterTextPlugin")) {
        Chart.register(donutCenterTextPlugin);
    }

    const revenueCanvas = document.getElementById("revenueChart");
    if (revenueCanvas) {
        const revenueContext = revenueCanvas.getContext("2d");
        const revenueGradient = revenueContext.createLinearGradient(0, 0, 0, 320);
        revenueGradient.addColorStop(0, "rgba(139, 92, 246, 0.36)");
        revenueGradient.addColorStop(1, "rgba(139, 92, 246, 0.02)");

        new Chart(revenueCanvas, {
            type: "line",
            data: {
                labels: revenueLabels,
                datasets: [{
                    label: "Revenue (₹)",
                    data: revenueData,
                    borderColor: "#8b5cf6",
                    backgroundColor: revenueGradient,
                    borderWidth: 3,
                    tension: 0.36,
                    fill: true,
                    pointRadius: 0,
                    pointHoverRadius: 5,
                    pointHoverBackgroundColor: "#c4b5fd",
                    pointHoverBorderColor: "#ffffff"
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    ...commonPlugins,
                    tooltip: {
                        ...commonPlugins.tooltip,
                        callbacks: {
                            label(context) {
                                return "₹" + Number(context.raw || 0).toLocaleString("en-IN");
                            }
                        }
                    }
                },
                scales: {
                    y: {
                        ...baseAxis,
                        beginAtZero: true,
                        ticks: {
                            ...baseAxis.ticks,
                            callback(value) {
                                return "₹" + Number(value).toLocaleString("en-IN");
                            }
                        }
                    },
                    x: {
                        ...baseAxis,
                        grid: { display: false }
                    }
                }
            }
        });
    }

    const chartColors = ["#8b5cf6", "#3b82f6", "#22c55e", "#f59e0b"];

    const buildDoughnut = function (canvasId, centerTop, centerBottom) {
        const canvas = document.getElementById(canvasId);
        if (!canvas) {
            return;
        }

        new Chart(canvas, {
            type: "doughnut",
            data: {
                labels: planLabels,
                datasets: [{
                    data: planData,
                    backgroundColor: chartColors.slice(0, planLabels.length),
                    borderColor: "rgba(9, 13, 22, 0.96)",
                    borderWidth: 2,
                    hoverOffset: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: "72%",
                plugins: {
                    ...commonPlugins,
                    legend: {
                        position: "right",
                        labels: {
                            color: axisColor,
                            font: chartFont,
                            boxWidth: 10,
                            boxHeight: 10,
                            padding: 14
                        }
                    },
                    donutCenterTextPlugin: {
                        textTop: centerTop,
                        textBottom: centerBottom
                    },
                    tooltip: {
                        ...commonPlugins.tooltip,
                        callbacks: {
                            label(context) {
                                const value = Number(context.raw || 0);
                                const total = context.dataset.data.reduce((sum, item) => sum + Number(item || 0), 0);
                                const percentage = total > 0 ? ((value / total) * 100).toFixed(1) : "0.0";
                                return `${context.label}: ${value} (${percentage}%)`;
                            }
                        }
                    }
                }
            }
        });
    };

    buildDoughnut("planChart", "₹" + totalRevenue.toLocaleString("en-IN"), "Total Revenue");
    buildDoughnut("mixChart", String(totalShops), "Total Shops");

    const usersCanvas = document.getElementById("usersChart");
    if (usersCanvas) {
        new Chart(usersCanvas, {
            type: "line",
            data: {
                labels: revenueLabels,
                datasets: [{
                    label: "Users",
                    data: usersData,
                    borderColor: "#3b82f6",
                    backgroundColor: "rgba(59, 130, 246, 0.12)",
                    borderWidth: 3,
                    tension: 0.34,
                    fill: false,
                    pointRadius: 0,
                    pointHoverRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: commonPlugins,
                scales: {
                    y: {
                        ...baseAxis,
                        beginAtZero: true,
                        ticks: {
                            ...baseAxis.ticks,
                            stepSize: 1
                        }
                    },
                    x: {
                        ...baseAxis,
                        grid: { display: false }
                    }
                }
            }
        });
    }

    const ticketCanvas = document.getElementById("ticketChart");
    if (ticketCanvas) {
        new Chart(ticketCanvas, {
            type: "line",
            data: {
                labels: ticketLabels,
                datasets: [{
                    label: "Tickets",
                    data: ticketData,
                    borderColor: "#ef4444",
                    backgroundColor: "rgba(239, 68, 68, 0.12)",
                    borderWidth: 3,
                    tension: 0.34,
                    fill: false,
                    pointRadius: 0,
                    pointHoverRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    ...commonPlugins,
                    tooltip: {
                        ...commonPlugins.tooltip,
                        callbacks: {
                            label(context) {
                                return `${context.raw || 0} tickets`;
                            }
                        }
                    }
                },
                scales: {
                    y: {
                        ...baseAxis,
                        beginAtZero: true,
                        ticks: {
                            ...baseAxis.ticks,
                            stepSize: 1
                        }
                    },
                    x: {
                        ...baseAxis,
                        grid: { display: false }
                    }
                }
            }
        });
    }

    const clockNode = document.getElementById("cockpitClock");
    if (clockNode) {
        const updateClock = function () {
            const formatter = new Intl.DateTimeFormat("en-IN", {
                hour: "2-digit",
                minute: "2-digit",
                second: "2-digit",
                hour12: true,
                timeZone: "Asia/Kolkata"
            });
            clockNode.textContent = formatter.format(new Date());
        };

        updateClock();
        window.setInterval(updateClock, 1000);
    }
};
