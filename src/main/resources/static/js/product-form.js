const csrfToken = document.querySelector("meta[name='_csrf']")?.content;
const csrfHeader = document.querySelector("meta[name='_csrf_header']")?.content;

const barcodeInput = document.getElementById("barcodeInput");
const barcodeStatus = document.getElementById("barcodeStatus");
const productForm = document.getElementById("productForm");
const generateBarcodeBtn = document.getElementById("generateBarcodeBtn");
const repeatLabelsButtons = document.querySelectorAll("[data-repeat-labels-product-id]");
const labelSheetModal = document.getElementById("labelSheetModal");
const labelSheetForm = document.getElementById("labelSheetForm");
const labelSheetProductIdInput = document.getElementById("labelSheetProductId");
const labelSheetProductName = document.getElementById("labelSheetProductName");
const labelSheetQuantityInput = document.getElementById("labelSheetQuantity");
const labelSheetSizeSelect = document.getElementById("labelSheetSize");
const labelSheetNote = document.getElementById("labelSheetNote");
const labelSheetCountInfo = document.getElementById("labelSheetCountInfo");

const LABEL_SHEET_PRESETS = {
    SHEET_20: {
        count: 20,
        label: "20 per sheet",
        description: "1 A4 sheet fits 20 stickers. Best for bigger labels, boxes, and shelf labels."
    },
    SHEET_24: {
        count: 24,
        label: "24 per sheet",
        description: "1 A4 sheet fits 24 stickers. Good medium-size labels for bottles and general packs."
    },
    SHEET_40: {
        count: 40,
        label: "40 per sheet",
        description: "1 A4 sheet fits 40 stickers. Best for small product stickers and common barcode paper."
    },
    SHEET_48: {
        count: 48,
        label: "48 per sheet",
        description: "1 A4 sheet fits 48 stickers. Good for compact barcode tagging with limited text."
    },
    SHEET_65: {
        count: 65,
        label: "65 per sheet",
        description: "1 A4 sheet fits 65 stickers. Small labels with tighter barcode and text space."
    },
    SHEET_80: {
        count: 80,
        label: "80 per sheet",
        description: "1 A4 sheet fits 80 stickers. Very small labels, best when you need maximum density."
    }
};

let barcodeCheckTimer = null;
let barcodeCheckController = null;

function updateBarcodeStatus(state, message) {
    if (!barcodeStatus) {
        return;
    }

    barcodeStatus.className = `barcode-status barcode-status-${state}`;

    const iconClass = state === "ready"
        ? "fa-check-circle"
        : state === "duplicate" || state === "invalid"
            ? "fa-exclamation-circle"
            : state === "checking"
                ? "fa-spinner fa-spin"
                : "fa-wave-square";

    barcodeStatus.innerHTML = `<i class="fas ${iconClass}"></i><span>${message}</span>`;
}

function normalizeBarcode(value) {
    return (value || "").replace(/\s+/g, "").trim();
}

async function checkBarcodeAvailability(value) {
    const normalizedValue = normalizeBarcode(value);
    const productId = productForm?.querySelector("input[name='id']")?.value?.trim();

    if (!normalizedValue) {
        updateBarcodeStatus("idle", "Add a manufacturer barcode now, or leave it blank and fill it later when stock arrives.");
        return;
    }

    if (!/^[A-Za-z0-9._/-]+$/.test(normalizedValue)) {
        updateBarcodeStatus("invalid", "Use only letters, numbers, dot, slash, underscore, or hyphen in barcode values.");
        return;
    }

    updateBarcodeStatus("checking", "Checking whether this barcode is already used inside your shop...");

    if (barcodeCheckController) {
        barcodeCheckController.abort();
    }
    barcodeCheckController = new AbortController();

    const params = new URLSearchParams({ barcode: normalizedValue });
    if (productId) {
        params.set("productId", productId);
    }

    try {
        const response = await fetch(`/products/barcode-check?${params.toString()}`, {
            headers: csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {},
            signal: barcodeCheckController.signal
        });

        if (!response.ok) {
            throw new Error("Failed to verify barcode");
        }

        const payload = await response.json();
        if (barcodeInput) {
            barcodeInput.value = payload.normalizedBarcode || normalizedValue;
        }
        updateBarcodeStatus(payload.state || "idle", payload.message || "Barcode status updated.");
    } catch (error) {
        if (error.name === "AbortError") {
            return;
        }
        updateBarcodeStatus("invalid", "Could not verify barcode right now. You can still save, but uniqueness will be checked again on submit.");
    }
}

if (barcodeInput) {
    barcodeInput.addEventListener("input", () => {
        barcodeInput.value = normalizeBarcode(barcodeInput.value);
        if (barcodeCheckTimer) {
            window.clearTimeout(barcodeCheckTimer);
        }
        barcodeCheckTimer = window.setTimeout(() => {
            checkBarcodeAvailability(barcodeInput.value);
        }, 220);
    });

    barcodeInput.addEventListener("blur", () => {
        barcodeInput.value = normalizeBarcode(barcodeInput.value);
        checkBarcodeAvailability(barcodeInput.value);
    });

    barcodeInput.addEventListener("keydown", event => {
        if (event.key === "Enter") {
            event.preventDefault();
            barcodeInput.value = normalizeBarcode(barcodeInput.value);
            checkBarcodeAvailability(barcodeInput.value);
        }
    });
}

if (generateBarcodeBtn) {
    generateBarcodeBtn.addEventListener("click", async () => {
        const productId = generateBarcodeBtn.dataset.productId;
        if (!productId) {
            return;
        }

        updateBarcodeStatus("checking", "Generating a unique internal barcode for this medicine...");
        generateBarcodeBtn.disabled = true;

        try {
            const response = await fetch(`/products/${productId}/generate-barcode`, {
                method: "POST",
                headers: {
                    ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {})
                }
            });

            if (!response.ok) {
                throw new Error("Failed to generate barcode");
            }

            const payload = await response.json();
            if (barcodeInput) {
                barcodeInput.value = payload.barcode || "";
            }
            updateBarcodeStatus("ready", payload.message || "Internal barcode generated and saved.");
            if (payload.labelUrl) {
                window.open(payload.labelUrl, "_blank", "noopener,noreferrer");
            }
            generateBarcodeBtn.remove();
        } catch (_) {
            updateBarcodeStatus("invalid", "Could not generate barcode right now. Please retry once.");
            generateBarcodeBtn.disabled = false;
        }
    });
}

function closeLabelSheetModal() {
    labelSheetModal?.classList.remove("show");
}

window.closeLabelSheetModal = closeLabelSheetModal;

function updateLabelSheetHelper() {
    const preset = LABEL_SHEET_PRESETS[labelSheetSizeSelect?.value || "SHEET_40"] || LABEL_SHEET_PRESETS.SHEET_40;
    const quantity = Number.parseInt(labelSheetQuantityInput?.value || "0", 10);
    const safeQuantity = Number.isFinite(quantity) && quantity > 0 ? quantity : 0;
    const sheetCount = safeQuantity ? Math.ceil(safeQuantity / preset.count) : 0;

    if (labelSheetNote) {
        labelSheetNote.textContent = preset.description;
    }
    if (labelSheetCountInfo) {
        labelSheetCountInfo.textContent = safeQuantity
            ? `Printing ${safeQuantity} labels will use ${sheetCount} A4 sheet${sheetCount === 1 ? "" : "s"}.`
            : `1 A4 sheet fits ${preset.count} stickers.`;
    }
}

function setLabelSheetQuantityFromPreset() {
    const preset = LABEL_SHEET_PRESETS[labelSheetSizeSelect?.value || "SHEET_40"] || LABEL_SHEET_PRESETS.SHEET_40;
    if (labelSheetQuantityInput) {
        labelSheetQuantityInput.value = String(preset.count);
    }
    updateLabelSheetHelper();
}

repeatLabelsButtons.forEach((button) => {
    button.addEventListener("click", () => {
        const productId = button.dataset.repeatLabelsProductId;
        if (!productId || !labelSheetModal || !labelSheetForm) {
            return;
        }

        if (labelSheetProductIdInput) {
            labelSheetProductIdInput.value = productId;
        }
        if (labelSheetProductName) {
            labelSheetProductName.textContent = button.dataset.repeatLabelsProductName || "this medicine";
        }
        if (labelSheetSizeSelect) {
            labelSheetSizeSelect.value = "SHEET_40";
        }

        setLabelSheetQuantityFromPreset();
        labelSheetModal.classList.add("show");
        labelSheetQuantityInput?.focus();
        labelSheetQuantityInput?.select();
    });
});

labelSheetModal?.addEventListener("click", (event) => {
    if (event.target === labelSheetModal) {
        closeLabelSheetModal();
    }
});

labelSheetQuantityInput?.addEventListener("input", updateLabelSheetHelper);
labelSheetSizeSelect?.addEventListener("change", setLabelSheetQuantityFromPreset);

labelSheetForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    const productId = labelSheetProductIdInput?.value?.trim();
    const quantity = Number.parseInt(labelSheetQuantityInput?.value || "", 10);
    const size = (labelSheetSizeSelect?.value || "SHEET_40").toUpperCase();

    if (!productId) {
        return;
    }
    if (!Number.isFinite(quantity) || quantity < 1 || quantity > 500) {
        updateBarcodeStatus("invalid", "Enter a label quantity between 1 and 500.");
        labelSheetQuantityInput?.focus();
        return;
    }

    window.open(`/products/${productId}/barcode-sheet?quantity=${encodeURIComponent(quantity)}&size=${encodeURIComponent(size)}`, "_blank", "noopener,noreferrer");
    const preset = LABEL_SHEET_PRESETS[size] || LABEL_SHEET_PRESETS.SHEET_40;
    updateBarcodeStatus("ready", `Opening ${quantity} labels using ${preset.label || preset.count + " per sheet"} format for print.`);
    closeLabelSheetModal();
});

document.addEventListener("DOMContentLoaded", () => {
    const firstInput = document.querySelector("input[type='text']");
    firstInput?.focus();

    document.querySelectorAll("input[name='price'], input[name='mrp'], input[name='purchasePrice']").forEach((field) => {
        field.addEventListener("blur", function onBlur() {
            if (this.value) {
                this.value = parseFloat(this.value).toFixed(2);
            }
        });
    });

    if (barcodeInput?.value) {
        barcodeInput.value = normalizeBarcode(barcodeInput.value);
        checkBarcodeAvailability(barcodeInput.value);
    }

    setLabelSheetQuantityFromPreset();
});
