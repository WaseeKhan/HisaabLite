function toggleUserMenu() {
    const menu = document.getElementById("adminUserDropdown");
    menu.style.display = menu.style.display === "block" ? "none" : "block";
}

document.addEventListener("click", function(e) {
    const menu = document.getElementById("adminUserDropdown");
    const trigger = document.querySelector(".admin-user-trigger");

    if (!trigger.contains(e.target)) {
        menu.style.display = "none";
    }
});


function applyAdminTheme(theme) {
    document.body.classList.remove("theme-dark", "theme-light");
    document.body.classList.add(theme);

    const icon = document.getElementById("adminThemeIcon");
    if (icon) {
        icon.className = theme === "theme-light" ? "fas fa-sun" : "fas fa-moon";
    }

    localStorage.setItem("adminTheme", theme);
}

function toggleAdminTheme() {
    const isLight = document.body.classList.contains("theme-light");
    applyAdminTheme(isLight ? "theme-dark" : "theme-light");
}

document.addEventListener("DOMContentLoaded", function () {
    const savedTheme = localStorage.getItem("adminTheme") || "theme-dark";
    applyAdminTheme(savedTheme);
});