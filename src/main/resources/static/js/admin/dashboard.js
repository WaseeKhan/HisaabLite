window.initAdminDashboardCharts = function initAdminDashboardCharts(config) {
    const revenueLabels = config.revenueLabels || [];
    const revenueData = config.revenueData || [];
    const planLabels = config.planLabels || ["FREE", "BASIC", "PRO"];
    const planData = config.planData || [0, 0, 0];
    const usersData = config.usersData || [];
    const ticketData = config.ticketData || [0, 0, 0, 0, 0, 0, 0];

    const baseAxis = {
        grid: { color: "#edf2f7", lineWidth: 1 },
        ticks: {
            color: "#64748b",
            font: { family: "Manrope", size: 11 }
        }
    };

    const labelFont = {
        family: "Manrope",
        size: 11
    };

    const planColors = {
        FREE: "#94a3b8",
        BASIC: "#2563eb",
        PRO: "#7c3aed"
    };

    const backgroundColors = planLabels.map(function (label) {
        return planColors[label] || "#94a3b8";
    });

    const revenueCanvas = document.getElementById("revenueChart");
    if (revenueCanvas) {
        new Chart(revenueCanvas, {
            type: "line",
            data: {
                labels: revenueLabels,
                datasets: [{
                    label: "Revenue (₹)",
                    data: revenueData,
                    borderColor: "#2563eb",
                    backgroundColor: "rgba(37, 99, 235, 0.08)",
                    borderWidth: 2,
                    tension: 0.32,
                    fill: true,
                    pointRadius: 2,
                    pointHoverRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: function (context) {
                                return "₹" + Number(context.raw || 0).toFixed(2);
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
                            callback: function (value) {
                                return "₹" + value;
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

    const planCanvas = document.getElementById("planChart");
    if (planCanvas) {
        new Chart(planCanvas, {
            type: "doughnut",
            data: {
                labels: planLabels,
                datasets: [{
                    data: planData,
                    backgroundColor: backgroundColors,
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: "66%",
                plugins: {
                    legend: {
                        position: "bottom",
                        labels: {
                            color: "#475569",
                            font: labelFont,
                            boxWidth: 10,
                            padding: 14
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: function (context) {
                                const value = context.raw || 0;
                                const total = context.dataset.data.reduce(function (sum, item) {
                                    return sum + item;
                                }, 0);
                                const percentage = total > 0 ? ((value / total) * 100).toFixed(1) : "0.0";
                                return context.label + ": " + value + " shops (" + percentage + "%)";
                            }
                        }
                    }
                }
            }
        });
    }

    const usersCanvas = document.getElementById("usersChart");
    if (usersCanvas) {
        new Chart(usersCanvas, {
            type: "bar",
            data: {
                labels: revenueLabels,
                datasets: [{
                    label: "New Users",
                    data: usersData,
                    backgroundColor: "#10b981",
                    borderRadius: 6,
                    barPercentage: 0.62
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: function (context) {
                                return context.raw + " new users";
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

    const ticketCanvas = document.getElementById("ticketChart");
    if (ticketCanvas) {
        new Chart(ticketCanvas, {
            type: "line",
            data: {
                labels: revenueLabels,
                datasets: [{
                    label: "Tickets",
                    data: ticketData,
                    borderColor: "#f59e0b",
                    backgroundColor: "rgba(245, 158, 11, 0.08)",
                    borderWidth: 2,
                    tension: 0.28,
                    fill: true,
                    pointRadius: 2,
                    pointHoverRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: function (context) {
                                return context.raw + " tickets";
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
};
