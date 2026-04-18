// =========================
// Expygen Billing JS
// Keyboard-first billing flow
// =========================

const csrfToken = document.querySelector("meta[name='_csrf']")?.content;
const csrfHeader = document.querySelector("meta[name='_csrf_header']")?.content;

const searchInput = document.getElementById("searchInput");
const searchIcon = document.querySelector(".search-icon");
const qtyInput = document.getElementById("qtyInput");
const resultsBox = document.getElementById("searchResults");
const addBtn = document.getElementById("addBtn");
const searchModeBtn = document.getElementById("searchModeBtn");
const scanModeBtn = document.getElementById("scanModeBtn");
const scanStatusCard = document.getElementById("scanStatusCard");
const scanStatusTitle = document.getElementById("scanStatusTitle");
const scanStatusText = document.getElementById("scanStatusText");
const cartContainer = document.getElementById("cartContainer");
const billingForm = document.getElementById("billingForm");
const completeBtn = document.querySelector(".btn-complete");
const cancelPopup = document.getElementById("cancelPopup");
const paymentMode = document.getElementById("paymentMode");
const customerNameInput = document.querySelector('input[name="customerName"]');
const customerPhoneInput = document.querySelector('input[name="customerPhone"]');
const doctorNameInput = document.querySelector('input[name="doctorName"]');
const customerHistoryCard = document.getElementById("customerHistoryCard");
const customerHistoryBadge = document.getElementById("customerHistoryBadge");
const customerHistoryName = document.getElementById("customerHistoryName");
const customerHistoryMeta = document.getElementById("customerHistoryMeta");
const customerHistorySpend = document.getElementById("customerHistorySpend");
const customerHistoryDoctor = document.getElementById("customerHistoryDoctor");
const customerHistoryMedicinesWrap = document.getElementById("customerHistoryMedicinesWrap");
const customerHistoryMedicines = document.getElementById("customerHistoryMedicines");
const customerHistorySalesWrap = document.getElementById("customerHistorySalesWrap");
const customerHistorySales = document.getElementById("customerHistorySales");
const prescriptionVerifiedInput = document.getElementById("prescriptionVerified");
const prescriptionDateInput = document.getElementById("prescriptionDate");
const prescriptionReferenceInput = document.getElementById("prescriptionReference");
const prescriptionModePill = document.getElementById("prescriptionModePill");
const prescriptionBannerTitle = document.getElementById("prescriptionBannerTitle");
const prescriptionBannerText = document.getElementById("prescriptionBannerText");
const amountReceived = document.getElementById("amountReceived");
const discountAmount = document.getElementById("discountAmount");
const discountPercent = document.getElementById("discountPercent");
const successToast = document.getElementById("successToast");
const invoiceBtn = document.getElementById("invoiceBtn");
const printInvoiceBtn = document.getElementById("printInvoiceBtn");
const whatsappInvoiceBtn = document.getElementById("whatsappInvoiceBtn");
const lastInvoicePrintBtn = document.getElementById("lastInvoicePrintBtn");
const lastInvoiceWhatsappBtn = document.getElementById("lastInvoiceWhatsappBtn");
const successToastMessage = document.getElementById("successToastMessage");
const lastInvoiceBar = document.getElementById("lastInvoiceBar");
const lastInvoiceList = document.getElementById("lastInvoiceList");
const invoicePhoneModal = document.getElementById("invoicePhoneModal");
const invoicePhoneInput = document.getElementById("invoicePhoneInput");
const invoicePhoneSendBtn = document.getElementById("invoicePhoneSendBtn");

const BILLING_ENTRY_MODE_KEY = "expygen-billing-entry-mode";
const LAST_INVOICE_KEY = "expygen:lastInvoice";
const LAST_INVOICES_KEY = "expygen:lastInvoices";

let suggestions = [];
let selectedIndex = -1;
let selectedProduct = null;
let lastSavedInvoiceId = null;
let lastSavedPhone = null;
let recentInvoices = [];
let audioContext = null;
let entryMode = "search";
let lookupTimer = null;
let lookupRequestSequence = 0;
let customerHistoryTimer = null;
let customerHistoryRequestSequence = 0;
window.currentCart = [];
window.lastSavedInvoiceId = null;
window.lastSavedPhone = null;

function persistLastInvoiceContext() {
    if (!window.sessionStorage) return;

    if (!lastSavedInvoiceId) {
        window.sessionStorage.removeItem(LAST_INVOICE_KEY);
    } else {
        window.sessionStorage.setItem(LAST_INVOICE_KEY, JSON.stringify({
            invoiceId: lastSavedInvoiceId,
            phone: lastSavedPhone || null,
            customerName: customerNameInput?.value?.trim() || "Walk-in"
        }));
    }

    window.sessionStorage.setItem(
        LAST_INVOICES_KEY,
        JSON.stringify(recentInvoices.slice(0, 3))
    );
}

function restoreLastInvoiceContext() {
    if (!window.sessionStorage) return;

    try {
        const recentRaw = window.sessionStorage.getItem(LAST_INVOICES_KEY);
        recentInvoices = recentRaw ? JSON.parse(recentRaw) : [];

        if (!Array.isArray(recentInvoices)) {
            recentInvoices = [];
        }

        if (!lastSavedInvoiceId && recentInvoices.length) {
            lastSavedInvoiceId = recentInvoices[0].invoiceId || null;
            lastSavedPhone = recentInvoices[0].phone || null;
        }

        if (!lastSavedInvoiceId) {
            const raw = window.sessionStorage.getItem(LAST_INVOICE_KEY);
            if (!raw) return;

            const parsed = JSON.parse(raw);
            lastSavedInvoiceId = parsed.invoiceId || null;
            lastSavedPhone = parsed.phone || null;

            if (lastSavedInvoiceId) {
                recentInvoices = [{
                    invoiceId: lastSavedInvoiceId,
                    phone: lastSavedPhone || null,
                    customerName: parsed.customerName || "Walk-in"
                }, ...recentInvoices.filter(item => item?.invoiceId !== lastSavedInvoiceId)].slice(0, 3);
            }
        }
    } catch (_) {
        window.sessionStorage.removeItem(LAST_INVOICE_KEY);
        window.sessionStorage.removeItem(LAST_INVOICES_KEY);
    }
}


function refreshLastInvoiceBar() {
    window.lastSavedInvoiceId = lastSavedInvoiceId;
    window.lastSavedPhone = lastSavedPhone;

    if (!lastInvoiceBar || !lastInvoiceList || !recentInvoices.length) {
        if (lastInvoiceBar) {
            lastInvoiceBar.hidden = true;
        }
        return;
    }

    lastInvoiceBar.hidden = false;

    lastInvoiceList.innerHTML = recentInvoices.slice(0, 3).map((entry) => {
        const invoiceId = entry.invoiceId;
        const phone = entry.phone || "—";
        const customerName = entry.customerName || "Walk-in";
        const invoiceUrl = getInvoiceViewUrl(invoiceId);

        return `
            <div class="latest-bill-row">
                <div class="latest-bill-left">
                    <span class="latest-bill-invoice">Invoice_${invoiceId}</span>
                    <span class="latest-bill-separator">|</span>
                    <span class="latest-bill-phone">${escapeHtml(phone)}</span>
                    <span class="latest-bill-separator">|</span>
                    <span class="latest-bill-customer">${escapeHtml(customerName)}</span>
                </div>

                <div class="latest-bill-actions">
                    <a href="${invoiceUrl}" target="_blank" class="latest-bill-action" title="View">
                        <i class="fas fa-eye"></i>
                    </a>

                    <a href="/sales/invoice/${invoiceId}/thermal" target="_blank" class="latest-bill-action" title="Thermal">
                        <i class="fas fa-receipt"></i>
                    </a>

                    <button type="button" class="latest-bill-action" data-print-invoice="${invoiceId}" title="Print">
                        <i class="fas fa-print"></i>
                    </button>

                    <button type="button" class="latest-bill-action latest-bill-action-whatsapp" data-whatsapp-invoice="${invoiceId}" title="WhatsApp" ${entry.phone ? "" : "disabled"}>
                        <i class="fab fa-whatsapp"></i>
                    </button>
                </div>
            </div>
        `;
    }).join("");

    lastInvoiceList.querySelectorAll("[data-print-invoice]").forEach(button => {
        button.addEventListener("click", () => printInvoice(button.dataset.printInvoice));
    });

    lastInvoiceList.querySelectorAll("[data-whatsapp-invoice]").forEach(button => {
        button.addEventListener("click", () => {
            const invoiceId = button.dataset.whatsappInvoice;
            const match = recentInvoices.find(item => String(item.invoiceId) === String(invoiceId));
            sendInvoiceOnWhatsApp(invoiceId, match?.phone || null);
        });
    });

    if (invoiceBtn && lastSavedInvoiceId) {
        invoiceBtn.href = getInvoiceViewUrl(lastSavedInvoiceId);
    }

    if (whatsappInvoiceBtn) {
        whatsappInvoiceBtn.disabled = false;
    }
}

function escapeHtml(text) {
    if (!text) return "";
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}

function getProductSellableStock(product) {
    const sellableStock = Number.parseInt(product?.sellableStock ?? product?.stockQuantity ?? 0, 10);
    return Number.isFinite(sellableStock) ? Math.max(0, sellableStock) : 0;
}

function looksLikeBarcode(keyword) {
    const value = (keyword || "").trim();
    return value.length >= 4 && /^[A-Za-z0-9._/-]+$/.test(value);
}

function showTempToast(message, type = "success") {
    const toast = document.createElement("div");
    toast.className = "temp-toast";
    const backgrounds = {
        success: "#059669",
        error: "#dc2626",
        warning: "#d97706",
        info: "#2563eb"
    };
    const icons = {
        success: "fa-check-circle",
        error: "fa-exclamation-circle",
        warning: "fa-exclamation-triangle",
        info: "fa-info-circle"
    };
    toast.style.background = backgrounds[type] || backgrounds.info;
    toast.innerHTML = `<i class="fas ${icons[type] || icons.info}"></i><span>${escapeHtml(message)}</span>`;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 2200);
}

function getAudioContext() {
    if (audioContext) {
        return audioContext;
    }

    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) {
        return null;
    }

    audioContext = new AudioContextClass();
    return audioContext;
}

function playBeepSequence(tones) {
    const context = getAudioContext();
    if (!context) {
        return;
    }

    if (context.state === "suspended") {
        context.resume().catch(() => {});
    }

    const startTime = context.currentTime + 0.02;
    tones.forEach((tone, index) => {
        const oscillator = context.createOscillator();
        const gainNode = context.createGain();
        const toneStart = startTime + (tone.delay || 0);
        const duration = tone.duration || 0.08;
        const frequency = tone.frequency || 880;
        const volume = tone.volume || 0.035;

        oscillator.type = tone.type || "sine";
        oscillator.frequency.setValueAtTime(frequency, toneStart);

        gainNode.gain.setValueAtTime(0.0001, toneStart);
        gainNode.gain.exponentialRampToValueAtTime(volume, toneStart + 0.01);
        gainNode.gain.exponentialRampToValueAtTime(0.0001, toneStart + duration);

        oscillator.connect(gainNode);
        gainNode.connect(context.destination);

        oscillator.start(toneStart);
        oscillator.stop(toneStart + duration + 0.02);
    });
}

function playCartAddedSound() {
    playBeepSequence([
        { frequency: 880, duration: 0.07, volume: 0.03, type: "triangle" }
    ]);
}

function playSaleCompletedSound() {
    playBeepSequence([
        { frequency: 784, duration: 0.07, volume: 0.032, type: "triangle", delay: 0 },
        { frequency: 1046, duration: 0.11, volume: 0.04, type: "triangle", delay: 0.09 }
    ]);
}

function focusAndSelect(element) {
    if (!element) return;
    element.focus();
    if (typeof element.select === "function") {
        element.select();
    }
}

function focusSearch() {
    focusAndSelect(searchInput);
}

function persistEntryMode(mode) {
    try {
        window.localStorage?.setItem(BILLING_ENTRY_MODE_KEY, mode);
    } catch (_) {
        // Ignore storage failures.
    }
}

function restoreEntryMode() {
    try {
        const storedMode = window.localStorage?.getItem(BILLING_ENTRY_MODE_KEY);
        if (storedMode === "search" || storedMode === "scan") {
            return storedMode;
        }
    } catch (_) {
        // Ignore storage failures.
    }
    return "search";
}

function setScanStatus(type, title, message) {
    if (!scanStatusCard) return;
    scanStatusCard.classList.remove("scan-status-info", "scan-status-success", "scan-status-warning", "scan-status-error");
    scanStatusCard.classList.add(`scan-status-${type}`);
    if (scanStatusTitle) {
        scanStatusTitle.textContent = title;
    }
    if (scanStatusText) {
        scanStatusText.textContent = message;
    }
}

function hideCustomerHistory() {
    if (customerHistoryCard) {
        customerHistoryCard.hidden = true;
    }
    if (customerHistoryMedicines) {
        customerHistoryMedicines.innerHTML = "";
    }
    if (customerHistorySales) {
        customerHistorySales.innerHTML = "";
    }
    if (customerHistoryMedicinesWrap) {
        customerHistoryMedicinesWrap.hidden = true;
    }
    if (customerHistorySalesWrap) {
        customerHistorySalesWrap.hidden = true;
    }
}

function formatHistoryDate(dateValue) {
    if (!dateValue) return "—";
    const date = new Date(dateValue);
    if (Number.isNaN(date.getTime())) return "—";
    return new Intl.DateTimeFormat("en-IN", {
        day: "2-digit",
        month: "short",
        year: "numeric"
    }).format(date);
}

function renderCustomerHistory(history) {
    if (!history?.found || !customerHistoryCard) {
        hideCustomerHistory();
        return;
    }

    customerHistoryCard.hidden = false;
    if (customerHistoryBadge) {
        customerHistoryBadge.textContent = `${history.visitCount || 0} visits`;
    }
    if (customerHistoryName) {
        customerHistoryName.textContent = history.customerName || "Known customer";
    }
    if (customerHistoryMeta) {
        const parts = [];
        if (history.lastVisitDate) {
            parts.push(`Last visit ${formatHistoryDate(history.lastVisitDate)}`);
        }
        if (history.recentSales?.length) {
            parts.push(`Recent invoice #${history.recentSales[0].saleId}`);
        }
        customerHistoryMeta.textContent = parts.join(" • ") || "Recent customer history available";
    }
    if (customerHistorySpend) {
        customerHistorySpend.textContent = `₹${Number(history.lifetimeSpend || 0).toFixed(2)}`;
    }
    if (customerHistoryDoctor) {
        customerHistoryDoctor.textContent = history.lastDoctorName || "—";
    }

    if (customerHistoryMedicines && customerHistoryMedicinesWrap) {
        const medicines = Array.isArray(history.recentMedicines) ? history.recentMedicines : [];
        customerHistoryMedicines.innerHTML = medicines.map(name => `<span>${escapeHtml(name)}</span>`).join("");
        customerHistoryMedicinesWrap.hidden = medicines.length === 0;
    }

    if (customerHistorySales && customerHistorySalesWrap) {
        const sales = Array.isArray(history.recentSales) ? history.recentSales : [];
        customerHistorySales.innerHTML = sales.map(sale => `
            <div class="customer-history-sale">
                <div>
                    <strong>Invoice #${sale.saleId}</strong>
                    <span>${formatHistoryDate(sale.saleDate)}${sale.doctorName ? ` • ${escapeHtml(sale.doctorName)}` : ""}</span>
                </div>
                <strong>₹${Number(sale.totalAmount || 0).toFixed(2)}</strong>
            </div>
        `).join("");
        customerHistorySalesWrap.hidden = sales.length === 0;
    }

    if (customerNameInput && !customerNameInput.value?.trim() && history.customerName) {
        customerNameInput.value = history.customerName;
    }
    if (doctorNameInput && !doctorNameInput.value?.trim() && history.lastDoctorName) {
        doctorNameInput.value = history.lastDoctorName;
    }
}

function scheduleCustomerHistoryLookup() {
    const digits = (customerPhoneInput?.value || "").replace(/[^0-9]/g, "");
    if (customerHistoryTimer) {
        window.clearTimeout(customerHistoryTimer);
        customerHistoryTimer = null;
    }

    if (digits.length < 10) {
        hideCustomerHistory();
        return;
    }

    const requestId = ++customerHistoryRequestSequence;
    customerHistoryTimer = window.setTimeout(() => {
        fetch(`/sales/customer-history?phone=${encodeURIComponent(digits)}`)
            .then(response => response.ok ? response.json() : Promise.reject("history lookup failed"))
            .then(history => {
                if (requestId !== customerHistoryRequestSequence) {
                    return;
                }
                renderCustomerHistory(history);
            })
            .catch(() => {
                if (requestId !== customerHistoryRequestSequence) {
                    return;
                }
                hideCustomerHistory();
            });
    }, 260);
}

function refreshAddButton() {
    if (!addBtn) return;
    addBtn.innerHTML = entryMode === "scan"
        ? '<i class="fas fa-barcode"></i>  ADD'
        : '<i class="fas fa-search"></i>  ADD';
}

function updateModePresentation() {
    searchModeBtn?.classList.toggle("is-active", entryMode === "search");
    scanModeBtn?.classList.toggle("is-active", entryMode === "scan");

    if (searchInput) {
        searchInput.placeholder = entryMode === "scan"
            ? "Scan or type exact barcode and press Enter..."
            : "Search medicine, salt or scan barcode...";
    }

    if (searchIcon) {
        searchIcon.className = entryMode === "scan"
            ? "fas fa-barcode search-icon"
            : "fas fa-search search-icon";
    }

    refreshAddButton();
}

function setEntryMode(mode, options = {}) {
    const { persist = true, focus = false } = options;
    entryMode = mode === "scan" ? "scan" : "search";
    updateModePresentation();

    if (persist) {
        persistEntryMode(entryMode);
    }

    if (!searchInput?.value?.trim()) {
        if (entryMode === "scan") {
            setScanStatus("info", "Scanner Ready", "Use Scan mode, type the exact barcode, and press Enter. A USB scanner will work in the same field later.");
        } else {
            setScanStatus("info", "Search Ready", "Search by medicine, salt, or manufacturer. Switch to Scan mode for exact barcode billing.");
        }
    }

    if (focus) {
        focusSearch();
    }
}

function findExactBarcodeProduct(keyword, products = suggestions) {
    return (products || []).find(product => isExactBarcodeMatch(product, keyword)) || null;
}

async function fetchLookupResults(keyword) {
    const response = await fetch(`/sales/search?keyword=${encodeURIComponent(keyword)}`, {
        headers: csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {}
    });
    if (!response.ok) {
        throw new Error("Search failed");
    }
    return response.json();
}

function updateLookupStatus(keyword, products = suggestions) {
    const value = (keyword || "").trim();
    if (!value) {
        setEntryMode(entryMode, { persist: false });
        return;
    }

    const exactMatch = findExactBarcodeProduct(value, products);
    if (entryMode === "scan") {
        if (exactMatch) {
            const sellableStock = getProductSellableStock(exactMatch);
            if (sellableStock > 0) {
                setScanStatus("success", "Exact Barcode Ready", `${exactMatch.name} matched. Press Enter to add ${sellableStock} sellable unit${sellableStock === 1 ? "" : "s"} to the bill.`);
            } else {
                setScanStatus("warning", "Barcode Found, Stock Locked", `${exactMatch.name} matched, but there is no sellable batch stock available right now.`);
            }
            return;
        }

        if (products.length > 0) {
            setScanStatus("info", "Closest Matches Found", "Switch to Search mode if you want to pick by medicine name, salt, or manufacturer.");
        } else {
            setScanStatus("warning", "Barcode Not Found", "No sellable medicine matched this barcode. Check the code or switch to Search mode.");
        }
        return;
    }

    if (exactMatch && getProductSellableStock(exactMatch) > 0) {
        setScanStatus("success", "Exact Barcode Available", `${exactMatch.name} matched exactly. Press Enter to add it instantly or keep searching.`);
        return;
    }

    if (products.length > 0) {
        setScanStatus("info", "Search Results Ready", "Use arrow keys to move, Enter to select, or switch to Scan mode for exact barcode billing.");
    } else {
        setScanStatus("warning", "No Matching Medicine", "Try a brand name, salt, manufacturer, or switch to Scan mode for exact barcode lookup.");
    }
}

async function resolveExactBarcodeProduct(keyword) {
    const existingMatch = findExactBarcodeProduct(keyword);
    if (existingMatch) {
        return existingMatch;
    }

    if (!looksLikeBarcode(keyword)) {
        return null;
    }

    const products = await fetchLookupResults(keyword);
    renderSuggestions(products, keyword);
    return findExactBarcodeProduct(keyword, products);
}

async function handleBarcodeEntry(keyword) {
    const value = (keyword || "").trim();
    if (!value) {
        return;
    }

    selectedProduct = null;
    setScanStatus("info", "Matching Barcode", "Checking the exact barcode against sellable pharmacy stock...");

    try {
        const exactMatch = await resolveExactBarcodeProduct(value);
        if (!exactMatch) {
            selectedProduct = null;
            setScanStatus("warning", "Barcode Not Found", "No sellable medicine matched this barcode. Check the code or use Search mode.");
            showTempToast("No exact barcode match found", "warning");
            return;
        }

        const sellableStock = getProductSellableStock(exactMatch);
        if (sellableStock <= 0) {
            selectedProduct = null;
            setScanStatus("warning", "Barcode Found, Stock Locked", `${exactMatch.name} matched, but there is no sellable batch stock available right now.`);
            showTempToast("Matched medicine has no sellable stock", "warning");
            return;
        }

        autoAddScannedProduct(exactMatch);
    } catch (error) {
        console.error(error);
        setScanStatus("error", "Scan Failed", "Barcode lookup failed. Please retry or switch to Search mode.");
        showTempToast("Barcode lookup failed. Please try again.", "error");
    }
}

function closeSuggestions() {
    if (resultsBox) {
        resultsBox.innerHTML = "";
    }
    suggestions = [];
    selectedIndex = -1;
}

function selectSuggestion(product) {
    if (getProductSellableStock(product) <= 0) {
        setScanStatus("warning", "Medicine Not Sellable", "This medicine has no sellable stock right now. Check live batches or stock before billing.");
        showTempToast("This medicine has no sellable stock right now", "warning");
        focusSearch();
        return;
    }
    selectedProduct = product;
    if (searchInput) {
        searchInput.value = product.name;
    }
    closeSuggestions();
    focusAndSelect(qtyInput);
}

function getInvoiceViewUrl(invoiceId = lastSavedInvoiceId) {
    return invoiceId ? `/sales/invoice/${invoiceId}` : null;
}

function getThermalPrintUrl(invoiceId = lastSavedInvoiceId) {
    return invoiceId ? `/sales/invoice/${invoiceId}/thermal?autoprint=true` : null;
}

function printInvoice(invoiceId = lastSavedInvoiceId) {
    const thermalUrl = getThermalPrintUrl(invoiceId);
    if (!thermalUrl) {
        showTempToast("No completed invoice available yet", "warning");
        return;
    }
    const thermalWindow = window.open(
        thermalUrl,
        "_blank",
        "noopener,noreferrer,width=420,height=760"
    );
    if (!thermalWindow) {
        showTempToast("Popup blocked. Please allow popups for thermal printing.", "warning");
        return;
    }
    thermalWindow.focus();
}

function sendInvoiceOnWhatsApp(invoiceId = lastSavedInvoiceId, phoneNumber = lastSavedPhone) {
    if (!invoiceId) {
        showTempToast("No completed invoice available yet", "warning");
        return;
    }
    if (!phoneNumber) {
        openInvoicePhoneModal();
        return;
    }

    const formData = new URLSearchParams();
    formData.append("phoneNumber", phoneNumber);

    fetch(`/sales/invoice/${invoiceId}/send-whatsapp-pdf`, {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded",
            ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {})
        },
        body: formData.toString()
    })
        .then(async response => {
            const payload = await response.json().catch(() => ({}));
            if (!response.ok) {
                throw new Error(payload.error || "Failed to send invoice");
            }
            return payload;
        })
        .then(() => showTempToast("Invoice sent on WhatsApp", "success"))
        .catch(error => showTempToast(error.message || "WhatsApp sending failed", "error"));
}

function openInvoicePhoneModal() {
    if (!invoicePhoneModal) {
        showTempToast("Customer phone number is required for WhatsApp", "warning");
        focusAndSelect(customerPhoneInput);
        return;
    }
    invoicePhoneModal.classList.add("show");
    if (invoicePhoneInput) {
        invoicePhoneInput.value = lastSavedPhone || customerPhoneInput?.value || "";
        focusAndSelect(invoicePhoneInput);
    }
}

function hideInvoicePhoneModal() {
    invoicePhoneModal?.classList.remove("show");
}

window.hideInvoicePhoneModal = hideInvoicePhoneModal;

function showPopup() {
    cancelPopup?.classList.add("show");
}

function hidePopup() {
    cancelPopup?.classList.remove("show");
}

function resetBillingFields() {
    if (customerNameInput) customerNameInput.value = "";
    if (customerPhoneInput) customerPhoneInput.value = "";
    hideCustomerHistory();
    if (doctorNameInput) {
        doctorNameInput.value = "";
        delete doctorNameInput.dataset.touched;
    }
    if (prescriptionVerifiedInput) prescriptionVerifiedInput.checked = false;
    if (prescriptionDateInput) prescriptionDateInput.value = "";
    if (prescriptionReferenceInput) prescriptionReferenceInput.value = "";
    if (paymentMode) paymentMode.value = "CASH";
    if (amountReceived) amountReceived.value = "";
    if (discountAmount) discountAmount.value = "0";
    if (discountPercent) discountPercent.value = "0";
    const ids = [
        ["changeAmount", "0.00"],
        ["discountDisplay", "0.00"],
        ["finalTotalAmount", "0.00"],
        ["cartTotalSpotlight", "0.00"],
        ["cartTaxSpotlight", "0.00"],
        ["subtotalAmount", "0.00"],
        ["cgstAmount", "0.00"],
        ["sgstAmount", "0.00"],
        ["totalGstAmount", "0.00"],
        ["cartCount", "0 items"],
        ["cartItemCountValue", "0"],
        ["changeReturned", "0.00"]
    ];
    ids.forEach(([id, value]) => {
        const element = document.getElementById(id);
        if (!element) return;
        if ("value" in element) {
            element.value = value;
        } else {
            element.innerText = value;
        }
    });
}

function readErrorMessage(response) {
    return response.text().then(text => text || "Request failed");
}

function calculateTotals() {
    let subtotal = 0;
    let totalCgst = 0;
    let totalSgst = 0;
    let totalGst = 0;
    let totalWithTax = 0;

    window.currentCart.forEach(item => {
        const price = parseFloat(item.price || 0);
        const quantity = parseInt(item.quantity || 0, 10);
        const gstPercent = parseFloat(item.gstPercent || 0);

        const basePrice = price * quantity;
        const gstAmount = basePrice * (gstPercent / 100);

        subtotal += basePrice;
        totalCgst += gstAmount / 2;
        totalSgst += gstAmount / 2;
        totalGst += gstAmount;
        totalWithTax += basePrice + gstAmount;
    });

    const subtotalAmount = document.getElementById("subtotalAmount");
    const cgstAmount = document.getElementById("cgstAmount");
    const sgstAmount = document.getElementById("sgstAmount");
    const totalGstAmount = document.getElementById("totalGstAmount");

    if (subtotalAmount) {
        subtotalAmount.innerText = subtotal.toFixed(2);
    }
    if (cgstAmount) {
        cgstAmount.innerText = totalCgst.toFixed(2);
    }
    if (sgstAmount) {
        sgstAmount.innerText = totalSgst.toFixed(2);
    }
    if (totalGstAmount) {
        totalGstAmount.innerText = totalGst.toFixed(2);
    }
    const cartTaxSpotlight = document.getElementById("cartTaxSpotlight");
    if (cartTaxSpotlight) {
        cartTaxSpotlight.innerText = totalGst.toFixed(2);
    }

    return { subtotal, totalWithTax };
}

function calculateChange() {
    const { totalWithTax } = calculateTotals();
    let discount = parseFloat(discountAmount?.value) || 0;
    const discountPct = parseFloat(discountPercent?.value) || 0;

    if (discountPct > 0) {
        discount = totalWithTax * (discountPct / 100);
    }

    const netPayable = Math.max(0, totalWithTax - discount);
    const received = parseFloat(amountReceived?.value) || 0;
    const change = Math.max(0, received - netPayable);

    document.getElementById("changeAmount").innerText = change.toFixed(2);
    document.getElementById("changeReturned").value = change.toFixed(2);
    document.getElementById("discountDisplay").innerText = discount.toFixed(2);
    document.getElementById("finalTotalAmount").innerText = netPayable.toFixed(2);
    const cartTotalSpotlight = document.getElementById("cartTotalSpotlight");
    if (cartTotalSpotlight) {
        cartTotalSpotlight.innerText = netPayable.toFixed(2);
    }

    return { totalWithTax, discount, netPayable, received, change };
}

function cartRequiresPrescription() {
    return Array.isArray(window.currentCart) && window.currentCart.some(item => !!item.prescriptionRequired);
}

function refreshPrescriptionState() {
    const requiresPrescription = cartRequiresPrescription();
    if (!prescriptionModePill || !prescriptionBannerTitle || !prescriptionBannerText) {
        return requiresPrescription;
    }

    prescriptionModePill.textContent = requiresPrescription ? "Required" : "Optional";
    prescriptionModePill.classList.toggle("billing-pill-rx", requiresPrescription);
    const prescriptionBanner = document.getElementById("prescriptionBanner");
    if (prescriptionBanner) {
        prescriptionBanner.hidden = !requiresPrescription;
    }

    if (requiresPrescription) {
        prescriptionBannerTitle.textContent = "Rx medicine present in this bill";
        prescriptionBannerText.textContent = "Verify the prescription before completing the sale. Record prescription date and doctor name or prescription reference for proper traceability.";
    } else {
        if (prescriptionVerifiedInput) prescriptionVerifiedInput.checked = false;
        if (prescriptionDateInput) prescriptionDateInput.value = "";
        if (prescriptionReferenceInput) prescriptionReferenceInput.value = "";
        if (doctorNameInput && !doctorNameInput.dataset.touched) {
            doctorNameInput.value = "";
        }
    }

    return requiresPrescription;
}

window.updateCartUI = function updateCartUI(cart) {
    window.currentCart = cart || [];
    if (!cartContainer) return;

    if (!window.currentCart.length) {
        cartContainer.innerHTML = '<div class="empty-cart"><div class="empty-cart-graphic"><i class="fas fa-prescription-bottle-medical"></i></div><p>Counter cart is empty</p><span>Search a medicine or scan a barcode to begin billing.</span></div>';
        document.getElementById("cartCount").innerText = "0 items";
        const cartItemCountValue = document.getElementById("cartItemCountValue");
        if (cartItemCountValue) {
            cartItemCountValue.innerText = "0";
        }
        calculateChange();
        refreshPrescriptionState();
        return;
    }

    let html = "";
    window.currentCart.forEach((item, index) => {
        const price = parseFloat(item.price || 0);
        const qty = parseInt(item.quantity || 0, 10);
        const gst = parseFloat(item.gstPercent || 0);
        const basePrice = price * qty;
        const gstAmount = basePrice * (gst / 100);
        const totalWithGst = basePrice + gstAmount;
        const gstClass = gst === 0 ? "gst-zero" : gst <= 5 ? "gst-low" : gst <= 12 ? "gst-medium" : "gst-high";
        const metaBits = [];
        if (item.prescriptionRequired) metaBits.push("Rx medicine");
        metaBits.push(`${gst}% GST`);
        metaBits.push(`₹${price.toFixed(2)} each`);

        html += `
        <div class="cart-item" data-index="${index}">
            <div class="cart-item-main cart-item-main-compact">
                <div class="cart-item-col cart-item-col-product">
                    <div class="cart-item-product">
                        <span class="cart-item-dot ${item.prescriptionRequired ? "cart-item-dot-rx" : ""}"></span>
                        <div class="cart-item-info">
                            <div class="cart-item-title-row">
                                <div class="cart-item-name">${escapeHtml(item.productName)}</div>
                                <div class="cart-item-tags">
                                    <span class="gst-badge ${gstClass}">${gst}%</span>
                                    ${item.prescriptionRequired ? '<span class="suggestion-tag suggestion-tag-rx"><i class="fas fa-file-medical"></i> Rx</span>' : ''}
                                </div>
                            </div>
                            <div class="cart-item-meta">${metaBits.join(" • ")}</div>
                        </div>
                    </div>
                </div>

                <div class="cart-item-col cart-item-col-qty">
                    <div class="cart-item-qty">
                        <button class="qty-btn" onclick="window.changeQty(${index}, -1)" tabindex="-1">-</button>
                        <span>${qty}</span>
                        <button class="qty-btn" onclick="window.changeQty(${index}, 1)" tabindex="-1">+</button>
                    </div>
                </div>

                <div class="cart-item-col cart-item-col-price">₹${price.toFixed(2)}</div>

                <div class="cart-item-col cart-item-col-gst">
                    <span class="cart-item-gst-rate">${gst}%</span>
                    <small>₹${gstAmount.toFixed(2)}</small>
                </div>

                <div class="cart-item-col cart-item-col-total">
                    <div class="cart-item-total">₹${totalWithGst.toFixed(2)}</div>
                </div>

                <div class="cart-item-col cart-item-col-actions">
                    <button class="cart-item-remove" onclick="window.removeItem(${index})" tabindex="-1"><i class="fas fa-trash-alt"></i></button>
                </div>
            </div>
        </div>`;
    });

    cartContainer.innerHTML = html;
    document.getElementById("cartCount").innerText = `${window.currentCart.length} items`;
    const cartItemCountValue = document.getElementById("cartItemCountValue");
    if (cartItemCountValue) {
        cartItemCountValue.innerText = String(window.currentCart.length);
    }
    calculateChange();
    refreshPrescriptionState();
};

window.changeQty = function changeQty(index, delta) {
    const item = window.currentCart[index];
    if (!item) return;
    if ((item.quantity || 0) + delta < 1) return;

    const formData = new URLSearchParams();
    formData.append("_csrf", csrfToken);
    formData.append("index", index);
    formData.append("change", delta);

    fetch("/sales/update-qty", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formData.toString()
    })
        .then(response => response.ok ? response.json() : readErrorMessage(response).then(message => Promise.reject(message)))
        .then(updateCartUI)
        .catch(error => showTempToast(`Failed to update quantity: ${error}`, "error"));
};

window.removeItem = function removeItem(index) {
    const formData = new URLSearchParams();
    formData.append("_csrf", csrfToken);
    formData.append("index", index);
    formData.append("change", -9999);

    fetch("/sales/update-qty", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formData.toString()
    })
        .then(response => response.ok ? response.json() : readErrorMessage(response).then(message => Promise.reject(message)))
        .then(updateCartUI)
        .catch(error => showTempToast(`Failed to remove item: ${error}`, "error"));
};

function addProduct() {
    if (!selectedProduct) {
        showTempToast("Select a product first", "warning");
        focusSearch();
        return;
    }

    if (getProductSellableStock(selectedProduct) <= 0) {
        setScanStatus("warning", "Medicine Not Sellable", "This medicine has no sellable stock right now. Check live batches or stock before billing.");
        showTempToast("This medicine has no sellable stock right now", "warning");
        focusSearch();
        return;
    }

    const addedProductName = selectedProduct.name || "Product";
    const qty = parseInt(qtyInput?.value || "1", 10);
    addProductToCart(selectedProduct, qty, {
        successMessage: `${addedProductName} added to cart`
    });
}

function addProductToCart(product, quantity = 1, options = {}) {
    const {
        successMessage = `${product?.name || "Product"} added to cart`,
        onSuccess = null
    } = options;

    if (!product) {
        showTempToast("Select a product first", "warning");
        focusSearch();
        return;
    }

    const normalizedQuantity = Number.parseInt(quantity, 10);
    if (!Number.isFinite(normalizedQuantity) || normalizedQuantity < 1) {
        showTempToast("Quantity must be at least 1", "warning");
        focusAndSelect(qtyInput);
        return;
    }

    const formData = new URLSearchParams();
    formData.append("_csrf", csrfToken);
    formData.append("productId", product.id);
    formData.append("quantity", normalizedQuantity);

    fetch("/sales/add", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formData.toString()
    })
        .then(response => response.ok ? response.json() : response.text().then(text => Promise.reject(text)))
        .then(cartData => {
            updateCartUI(cartData);
            selectedProduct = null;
            if (searchInput) searchInput.value = "";
            if (qtyInput) qtyInput.value = "1";
            playCartAddedSound();
            showTempToast(successMessage, "success");
            setEntryMode(entryMode, { persist: false });
            focusSearch();
            if (typeof onSuccess === "function") {
                onSuccess();
            }
        })
        .catch(error => showTempToast(`Failed to add product: ${error}`, "error"));
}

function isExactBarcodeMatch(product, keyword) {
    const normalizedKeyword = (keyword || "").trim().toLowerCase();
    const barcode = (product?.barcode || "").trim().toLowerCase();
    return !!normalizedKeyword && !!barcode && barcode === normalizedKeyword;
}

function autoAddScannedProduct(product) {
    if (!product) return;
    const alreadyInCart = window.currentCart.find(item => item.productId === product.id);
    addProductToCart(product, 1, {
        successMessage: alreadyInCart
            ? `${product.name} quantity increased by 1`
            : `${product.name} scanned into cart`,
        onSuccess: () => {
            setScanStatus(
                "success",
                "Scan Added",
                alreadyInCart
                    ? `${product.name} was already in the bill, so the quantity was increased by 1.`
                    : `${product.name} was added to the bill. Scan the same barcode again to increase quantity quickly.`
            );
        }
    });
}

function renderSuggestions(products, keyword = searchInput?.value || "") {
    if (!resultsBox) return;
    closeSuggestions();

    if (!Array.isArray(products) || products.length === 0) {
        resultsBox.innerHTML = `<div class="suggestion-item empty">${entryMode === "scan" ? "No exact barcode match found" : "No matching product found"}</div>`;
        updateLookupStatus(keyword, []);
        return;
    }

    suggestions = products;
    products.forEach((product, index) => {
        const div = document.createElement("div");
        const gstPercent = Number(product.gstPercent || 0);
        const gstClass = gstPercent === 0 ? "gst-zero" : gstPercent <= 5 ? "gst-low" : gstPercent <= 12 ? "gst-medium" : "gst-high";
        const metadata = [product.genericName, product.manufacturer].filter(Boolean).map(escapeHtml).join(" • ");
        const chips = [];
        const sellableStock = getProductSellableStock(product);
        const exactMatch = isExactBarcodeMatch(product, keyword);
        const stockClass = sellableStock > 0 ? "suggestion-stock-available" : "suggestion-stock-empty";
        if (product.barcode) chips.push(`<span class="suggestion-tag"><i class="fas fa-barcode"></i> ${escapeHtml(product.barcode)}</span>`);
        if (product.packSize) chips.push(`<span class="suggestion-tag"><i class="fas fa-box-open"></i> ${escapeHtml(product.packSize)}</span>`);
        if (product.prescriptionRequired) chips.push('<span class="suggestion-tag suggestion-tag-rx"><i class="fas fa-file-medical"></i> Rx</span>');
        if (exactMatch) chips.unshift('<span class="suggestion-tag suggestion-tag-scan"><i class="fas fa-bolt"></i> Exact barcode</span>');
        div.className = `suggestion-item${exactMatch ? " suggestion-item-exact" : ""}${sellableStock <= 0 ? " suggestion-item-disabled" : ""}`;
        div.innerHTML = `
            <div class="suggestion-name">${escapeHtml(product.name)}</div>
            ${metadata ? `<div class="suggestion-meta">${metadata}</div>` : ""}
            <div class="suggestion-details">
                <span class="suggestion-price">₹${product.price}</span>
                <span class="suggestion-stock ${stockClass}"><i class="fas fa-box"></i> ${sellableStock} sellable</span>
                <span class="suggestion-gst ${gstClass}">${gstPercent}% GST</span>
            </div>
            ${chips.length ? `<div class="suggestion-tags">${chips.join("")}</div>` : ""}`;
        div.addEventListener("click", () => {
            if (sellableStock <= 0) {
                setScanStatus("warning", "Medicine Not Sellable", `${product.name} matched, but there is no sellable batch stock available right now.`);
                showTempToast("This medicine has no sellable stock right now", "warning");
                return;
            }
            selectedIndex = index;
            selectSuggestion(product);
        });
        resultsBox.appendChild(div);
    });
    updateLookupStatus(keyword, products);
}

function highlight(items) {
    items.forEach((element, index) => element.classList.toggle("active", index === selectedIndex));
    items[selectedIndex]?.scrollIntoView({ block: "nearest" });
}

if (searchInput) {
    searchInput.addEventListener("input", () => {
        const keyword = searchInput.value.trim();
        selectedProduct = null;
        if (lookupTimer) {
            window.clearTimeout(lookupTimer);
            lookupTimer = null;
        }
        if (!keyword) {
            closeSuggestions();
            setEntryMode(entryMode, { persist: false });
            return;
        }

        const delay = entryMode === "scan" ? 80 : 160;
        lookupTimer = window.setTimeout(() => {
            const requestId = ++lookupRequestSequence;
            fetchLookupResults(keyword)
                .then(products => {
                    if (searchInput.value.trim() !== keyword || requestId !== lookupRequestSequence) {
                        return;
                    }
                    renderSuggestions(products, keyword);
                })
                .catch(error => {
                    console.error(error);
                    setScanStatus("error", "Lookup Failed", "Medicine lookup failed. Please try again.");
                    showTempToast("Product search failed. Please try again.", "error");
                });
        }, delay);
    });

    searchInput.addEventListener("keydown", async event => {
        const items = document.querySelectorAll(".suggestion-item:not(.empty)");

        if (event.key === "ArrowDown" && items.length) {
            event.preventDefault();
            selectedIndex = (selectedIndex + 1) % items.length;
            highlight(items);
        } else if (event.key === "ArrowUp" && items.length) {
            event.preventDefault();
            selectedIndex = (selectedIndex - 1 + items.length) % items.length;
            highlight(items);
        } else if (event.key === "Enter" || (entryMode === "scan" && event.key === "Tab" && searchInput.value.trim())) {
            event.preventDefault();
            const keyword = searchInput.value.trim();
            const exactMatch = findExactBarcodeProduct(keyword);

            if (entryMode === "scan") {
                await handleBarcodeEntry(keyword);
                return;
            }

            if (selectedProduct) {
                focusAndSelect(qtyInput);
                return;
            }

            if (selectedIndex >= 0 && suggestions[selectedIndex]) {
                selectSuggestion(suggestions[selectedIndex]);
                focusAndSelect(qtyInput);
                return;
            } else if (exactMatch) {
                if (getProductSellableStock(exactMatch) > 0) {
                    selectSuggestion(exactMatch);
                    focusAndSelect(qtyInput);
                } else {
                    setScanStatus("warning", "Medicine Not Sellable", `${exactMatch.name} matched, but there is no sellable batch stock available right now.`);
                    showTempToast("Matched medicine has no sellable stock", "warning");
                }
            } else if (suggestions.length === 1) {
                selectSuggestion(suggestions[0]);
                focusAndSelect(qtyInput);
            } else if (looksLikeBarcode(keyword)) {
                await handleBarcodeEntry(keyword);
            }
        } else if (event.key === "Escape") {
            closeSuggestions();
        }
    });
}

if (addBtn) {
    addBtn.addEventListener("click", async () => {
        if (entryMode === "scan") {
            await handleBarcodeEntry(searchInput?.value || "");
            return;
        }
        addProduct();
    });
}
searchModeBtn?.addEventListener("click", () => setEntryMode("search", { focus: true }));
scanModeBtn?.addEventListener("click", () => setEntryMode("scan", { focus: true }));

if (qtyInput) {
    qtyInput.addEventListener("keydown", event => {
        if (event.key === "Enter") {
            event.preventDefault();
            addProduct();
        } else if (entryMode === "scan" && event.key === "Tab" && selectedProduct) {
            event.preventDefault();
            addProduct();
        }
    });
}

if (customerNameInput) {
    customerNameInput.addEventListener("keydown", event => {
        if (event.key === "Enter") {
            event.preventDefault();
            focusAndSelect(customerPhoneInput);
        }
    });
}

if (customerPhoneInput) {
    customerPhoneInput.addEventListener("input", () => {
        scheduleCustomerHistoryLookup();
    });
    customerPhoneInput.addEventListener("blur", () => {
        scheduleCustomerHistoryLookup();
    });
    customerPhoneInput.addEventListener("keydown", event => {
        if (event.key === "Enter") {
            event.preventDefault();
            paymentMode?.focus();
        }
    });
}

if (doctorNameInput) {
    doctorNameInput.addEventListener("input", () => {
        doctorNameInput.dataset.touched = "true";
    });
}

if (paymentMode) {
    paymentMode.addEventListener("change", () => {
        const { netPayable } = calculateChange();
        if ((paymentMode.value === "UPI" || paymentMode.value === "CARD") && amountReceived && !amountReceived.value) {
            amountReceived.value = netPayable.toFixed(2);
            calculateChange();
        }
    });
    paymentMode.addEventListener("keydown", event => {
        if (event.key === "Enter") {
            event.preventDefault();
            focusAndSelect(amountReceived);
        }
    });
}

if (amountReceived) {
    amountReceived.addEventListener("input", () => {
        if ((parseFloat(amountReceived.value) || 0) < 0) {
            amountReceived.value = "0";
        }
        calculateChange();
    });
    amountReceived.addEventListener("keydown", event => {
        if (event.key === "Enter") {
            event.preventDefault();
            billingForm?.requestSubmit();
        }
    });
}

if (discountAmount) {
    discountAmount.addEventListener("input", () => {
        if ((parseFloat(discountAmount.value) || 0) > 0 && discountPercent) {
            discountPercent.value = "0";
        }
        calculateChange();
    });
}

if (discountPercent) {
    discountPercent.addEventListener("input", () => {
        let value = parseFloat(discountPercent.value) || 0;
        if (value > 100) {
            value = 100;
            discountPercent.value = "100";
        }
        if (value > 0 && discountAmount) {
            discountAmount.value = "0";
        }
        calculateChange();
    });
}

if (billingForm) {
    billingForm.addEventListener("submit", event => {
        if (!window.currentCart || window.currentCart.length === 0) {
            event.preventDefault();
            showTempToast("Cart is empty", "warning");
            focusSearch();
            return;
        }

        const { netPayable, received } = calculateChange();
        const paymentValue = paymentMode?.value || "CASH";
        if ((paymentValue === "UPI" || paymentValue === "CARD") && received < netPayable) {
            event.preventDefault();
            showTempToast(`Amount received must cover ₹${netPayable.toFixed(2)} for ${paymentValue} payments`, "warning");
            focusAndSelect(amountReceived);
            return;
        }

        const requiresPrescription = refreshPrescriptionState();
        if (requiresPrescription) {
            if (!prescriptionVerifiedInput?.checked) {
                event.preventDefault();
                showTempToast("Verify the prescription before completing an Rx bill", "warning");
                prescriptionVerifiedInput?.focus();
                return;
            }

            if (!prescriptionDateInput?.value) {
                event.preventDefault();
                showTempToast("Prescription date is required for Rx medicines", "warning");
                focusAndSelect(prescriptionDateInput);
                return;
            }

            const doctorValue = doctorNameInput?.value?.trim() || "";
            const referenceValue = prescriptionReferenceInput?.value?.trim() || "";
            if (!doctorValue && !referenceValue) {
                event.preventDefault();
                showTempToast("Doctor name or prescription reference is required for Rx medicines", "warning");
                focusAndSelect(doctorNameInput || prescriptionReferenceInput);
                return;
            }
        }

        if (completeBtn) {
            completeBtn.disabled = true;
            completeBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Completing...';
        }
    });
}

if (printInvoiceBtn) {
    printInvoiceBtn.addEventListener("click", () => printInvoice());
}

if (lastInvoicePrintBtn) {
    lastInvoicePrintBtn.addEventListener("click", () => printInvoice());
}

if (whatsappInvoiceBtn) {
    whatsappInvoiceBtn.addEventListener("click", () => sendInvoiceOnWhatsApp());
}

if (lastInvoiceWhatsappBtn) {
    lastInvoiceWhatsappBtn.addEventListener("click", () => sendInvoiceOnWhatsApp());
}

if (invoicePhoneSendBtn) {
    invoicePhoneSendBtn.addEventListener("click", () => {
        const phone = invoicePhoneInput?.value?.trim();
        if (!phone) {
            showTempToast("Enter mobile number to send invoice", "warning");
            focusAndSelect(invoicePhoneInput);
            return;
        }
        if (!/^\d{10}$/.test(phone)) {
            showTempToast("Enter a valid 10-digit mobile number", "warning");
            focusAndSelect(invoicePhoneInput);
            return;
        }
        lastSavedPhone = phone;
        persistLastInvoiceContext();
        refreshLastInvoiceBar();
        hideInvoicePhoneModal();
        sendInvoiceOnWhatsApp(lastSavedInvoiceId, phone);
    });
}

if (invoicePhoneInput) {
    invoicePhoneInput.addEventListener("keydown", event => {
        if (event.key === "Enter") {
            event.preventDefault();
            invoicePhoneSendBtn?.click();
        }
    });
}

document.addEventListener("expygen:collect-commands", event => {
    if (!billingForm) {
        return;
    }

    event.detail.commands.push(
        {
            id: "billing:focus-search",
            title: "Billing: Focus product search",
            description: "Jump back to product search",
            keywords: "billing search product focus item scan",
            run: focusSearch
        },
        {
            id: "billing:focus-quantity",
            title: "Billing: Focus quantity",
            description: "Move to quantity input",
            keywords: "billing quantity qty focus",
            run: () => focusAndSelect(qtyInput)
        },
        {
            id: "billing:focus-payment",
            title: "Billing: Focus payment mode",
            description: "Jump to payment mode selector",
            keywords: "billing payment mode cash upi card",
            run: () => paymentMode?.focus()
        },
        {
            id: "billing:focus-amount",
            title: "Billing: Focus amount received",
            description: "Jump to amount received field",
            keywords: "billing amount received cash",
            run: () => focusAndSelect(amountReceived)
        },
        {
            id: "billing:complete",
            title: "Billing: Complete current bill",
            description: "Submit the bill with current cart",
            keywords: "billing complete submit save invoice",
            run: () => billingForm.requestSubmit()
        },
        {
            id: "billing:print",
            title: "Billing: Print last invoice",
            description: "Open the most recent invoice for printing",
            keywords: "billing print invoice latest",
            run: () => printInvoice()
        },
        {
            id: "billing:whatsapp",
            title: "Billing: Send last invoice on WhatsApp",
            description: "Send the most recent invoice to customer WhatsApp",
            keywords: "billing whatsapp send invoice latest",
            run: () => sendInvoiceOnWhatsApp()
        },
        {
            id: "billing:cancel",
            title: "Billing: Cancel current sale",
            description: "Cancel current cart and clear billing form",
            keywords: "billing cancel sale cart",
            run: () => showPopup()
        }
    );
});

document.addEventListener("keydown", event => {
    const activeTag = document.activeElement?.tagName;
    const isTyping = activeTag === "INPUT" || activeTag === "TEXTAREA" || activeTag === "SELECT";
    const popupOpen = cancelPopup?.classList.contains("show");

    if (event.key === "Escape") {
        if (invoicePhoneModal?.classList.contains("show")) {
            event.preventDefault();
            hideInvoicePhoneModal();
            focusSearch();
            return;
        }
        if (popupOpen) {
            event.preventDefault();
            hidePopup();
            focusSearch();
            return;
        }
        if (document.activeElement === searchInput) {
            closeSuggestions();
            selectedProduct = null;
        }
        focusSearch();
        return;
    }

    if (popupOpen) {
        if (event.key === "Enter") {
            event.preventDefault();
            confirmCancel();
        }
        return;
    }

    if (event.key === "F2") {
        event.preventDefault();
        focusSearch();
    } else if (event.key === "F3") {
        event.preventDefault();
        focusAndSelect(qtyInput);
    } else if (event.key === "F4") {
        event.preventDefault();
        paymentMode?.focus();
    } else if (event.key === "F6") {
        event.preventDefault();
        focusAndSelect(amountReceived);
    } else if (event.key === "F7") {
        event.preventDefault();
        focusAndSelect(discountAmount);
    } else if (event.key === "F8") {
        event.preventDefault();
        focusAndSelect(discountPercent);
    } else if (event.key === "F9") {
        event.preventDefault();
        sendInvoiceOnWhatsApp();
    } else if (event.key === "F10") {
        event.preventDefault();
        printInvoice();
    } else if (event.ctrlKey && event.key === "Enter") {
        event.preventDefault();
        billingForm?.requestSubmit();
    } else if (!isTyping && event.key === "Enter" && selectedProduct) {
        event.preventDefault();
        addProduct();
    }
});

function confirmCancel() {
    hidePopup();
    if (!csrfToken) {
        showTempToast("Security token missing. Refresh the page and try again.", "error");
        return;
    }

    const formData = new URLSearchParams();
    formData.append("_csrf", csrfToken);

    const cancelBtn = document.querySelector(".btn-cancel");
    const originalText = cancelBtn?.innerHTML;
    if (cancelBtn) {
        cancelBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Cancelling...';
        cancelBtn.disabled = true;
    }

    fetch("/sales/cancel", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formData.toString()
    })
        .then(response => response.ok ? response.json() : Promise.reject("Cancel failed"))
        .then(cartData => {
            updateCartUI(cartData);
            resetBillingFields();
            showTempToast("Sale cancelled successfully", "info");
            focusSearch();
        })
        .catch(error => showTempToast(`Failed to cancel sale: ${error}`, "error"))
        .finally(() => {
            if (cancelBtn) {
                cancelBtn.innerHTML = originalText;
                cancelBtn.disabled = false;
            }
        });
}

fetch("/sales/cart")
    .then(response => response.ok ? response.json() : Promise.resolve([]))
    .then(cart => updateCartUI(Array.isArray(cart) ? cart : []))
    .catch(() => updateCartUI([]));

window.onload = () => {
    const params = new URLSearchParams(window.location.search);
    restoreLastInvoiceContext();
    
 

    if (params.get("saved") === "true") {
    lastSavedInvoiceId = params.get("invoiceId");
    lastSavedPhone = params.get("phone");

    const latestCustomerName = customerNameInput?.value?.trim() || "Walk-in";

    if (lastSavedInvoiceId) {
        recentInvoices = [{
            invoiceId: lastSavedInvoiceId,
            phone: lastSavedPhone || null,
            customerName: latestCustomerName
        }, ...recentInvoices.filter(item => String(item?.invoiceId) !== String(lastSavedInvoiceId))].slice(0, 3);
    }

    persistLastInvoiceContext();

    if (invoiceBtn && lastSavedInvoiceId) {
        invoiceBtn.href = getInvoiceViewUrl(lastSavedInvoiceId);
    }

    if (successToastMessage) {
        successToastMessage.textContent = lastSavedPhone
            ? "Use F10 to print or F9 to send on WhatsApp."
            : "Use F10 to print. F9 lets you enter a phone number for WhatsApp.";
    }

    playSaleCompletedSound();
    successToast?.classList.add("show");
    setTimeout(() => successToast?.classList.remove("show"), 5000);
}

    refreshLastInvoiceBar();
    refreshPrescriptionState();
    setEntryMode(restoreEntryMode(), { persist: false });
    focusSearch();
};
