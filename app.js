// SwiftMart - Client Frontend Engine (app.js)
// Strict Monochrome Luxury Aesthetic (Black & White)

// --- Currency & Groq AI Engine ---
let currentCurrency = localStorage.getItem('swiftmart_currency') || 'PKR';
if (!['PKR', 'USD', 'GBP', 'EUR'].includes(currentCurrency)) {
    currentCurrency = 'PKR';
    localStorage.setItem('swiftmart_currency', 'PKR');
}
let GROQ_API_KEY = localStorage.getItem('swiftmart_groq_key') || "KEY";
const GROQ_MODEL = "MODEL ";
const GROQ_URL = "API KEY HERE";

let currencyRates = {
    PKR: { symbol: 'Rs. ', rate: 278.50, name: 'Pakistani Rupee', format: (val) => `Rs. ${Math.round(val).toLocaleString()}` },
    USD: { symbol: '$', rate: 1.0, name: 'US Dollar', format: (val) => `$${val.toFixed(2)}` },
    GBP: { symbol: '£', rate: 0.79, name: 'British Pound', format: (val) => `£${val.toFixed(2)}` },
    EUR: { symbol: '€', rate: 0.92, name: 'Euro', format: (val) => `€${val.toFixed(2)}` }
};

function formatCurrency(usd) {
    const c = currencyRates[currentCurrency] || currencyRates.PKR;
    const converted = (Number(usd) || 0) * c.rate;
    return c.format(converted);
}

function changeCurrency(code) {
    if (currencyRates[code]) {
        currentCurrency = code;
        localStorage.setItem('swiftmart_currency', code);
        const sel = document.getElementById('currency-select');
        if (sel) sel.value = code;
        renderCatalog(products);
        if (document.getElementById('drawer')?.classList.contains('on')) {
            renderCartDrawer();
        }
        if (currentModalProduct) {
            renderProductModal(currentModalProduct);
        }
        showToast(`Currency set to ${code} (${currencyRates[code].symbol.trim()})`);
    }
}

async function fetchRatesFromGroq() {
    const apiKey = (GROQ_API_KEY || localStorage.getItem('swiftmart_groq_key') || '').trim();
    if (!apiKey) {
        console.log("Groq API Key is empty. Operating with built-in financial rates.");
        return;
    }
    try {
        const prompt = 'Return a JSON object containing currency exchange rates for 1 USD against PKR, GBP, EUR. Return ONLY raw JSON like: {"PKR":278.5,"GBP":0.79,"EUR":0.92}';
        let res;
        const ratesPayload = JSON.stringify({
            model: GROQ_MODEL,
            messages: [
                { role: 'system', content: 'You are a currency exchange rate API. Output only raw valid JSON without markdown.' },
                { role: 'user', content: prompt }
            ],
            temperature: 0.1,
            max_completion_tokens: 1024
        });
        try {
            res = await fetch(GROQ_URL, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${apiKey}`
                },
                body: ratesPayload
            });
        } catch (fetchErr) {
            res = await fetch('/api/groq', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${apiKey}`
                },
                body: ratesPayload
            });
        }
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        const msg = data.choices?.[0]?.message;
        let content = (msg?.content || '').trim();
        if (!content && msg?.reasoning) {
            content = msg.reasoning.trim();
        }
        const m = content.match(/\{[\s\S]*\}/);
        if (m) {
            const parsed = JSON.parse(m[0]);
            for (const [k, rawVal] of Object.entries(parsed)) {
                const v = typeof rawVal === 'number' ? rawVal : parseFloat(rawVal);
                if (isNaN(v) || v <= 0) continue;
                const upper = k.toUpperCase();
                let targetKey = null;
                if (upper === 'PKR' || upper.endsWith('PKR') || upper.includes('PKR')) targetKey = 'PKR';
                else if (upper === 'GBP' || upper.endsWith('GBP') || upper.includes('GBP')) targetKey = 'GBP';
                else if (upper === 'EUR' || upper.endsWith('EUR') || upper.includes('EUR')) targetKey = 'EUR';
                if (targetKey && currencyRates[targetKey]) {
                    currencyRates[targetKey].rate = v;
                }
            }
            console.log("Updated live currency rates via Groq API:", parsed);
            renderCatalog(products);
        }
    } catch (e) {
        console.warn("Groq rate update error (fallback active):", e.message);
    }
}

function openGroqKeyModal() {
    closeGroqKeyModal();
    const c = document.getElementById('groq-modal-container');
    if (!c) return;
    const currentKey = GROQ_API_KEY || localStorage.getItem('swiftmart_groq_key') || '';
    const modal = document.createElement('div');
    modal.id = 'groq-modal-instance';
    modal.className = 'mb';
    modal.onclick = (e) => { if (e.target === modal) closeGroqKeyModal(); };
    modal.innerHTML = `
    <div style="background:var(--card);border:1.5px solid var(--bd);border-radius:20px;max-width:520px;width:100%;padding:32px;box-shadow:var(--sh);position:relative">
      <button class="x" onclick="closeGroqKeyModal()">✕</button>
      <h3 style="font-size:18px;font-weight:800;margin-bottom:8px">Groq Currency AI Setup</h3>
      <p style="color:var(--mut);font-size:13.5px;line-height:1.5;margin-bottom:16px">
        Groq API integration allows live currency exchange rate calculations.<br>
        <strong>Model:</strong> <code>${GROQ_MODEL}</code><br>
        <strong>Endpoint:</strong> <code>${GROQ_URL}</code>
      </p>
      <label style="display:block;font-size:13px;font-weight:700;margin-bottom:6px">Groq API Key (leave empty to use default rates)</label>
      <input type="password" id="groq-key-input" value="${escapeHtml(currentKey)}" placeholder="gsk_..." style="width:100%;padding:10px 14px;border-radius:10px;border:1.5px solid var(--bd);background:var(--bg);color:var(--fg);font-size:13.5px;margin-bottom:16px;box-sizing:border-box">
      <div style="display:flex;gap:10px;justify-content:flex-end">
        <button type="button" class="btn alt" onclick="clearGroqKey()">Clear Key</button>
        <button type="button" class="btn" onclick="saveGroqKey()">Save &amp; Refresh Rates</button>
      </div>
      <div id="groq-feedback" style="font-size:12.5px;margin-top:10px;text-align:center"></div>
    </div>
  `;
    c.appendChild(modal);
}

function closeGroqKeyModal() {
    const el = document.getElementById('groq-modal-instance');
    if (el) el.remove();
}

async function saveGroqKey() {
    const input = document.getElementById('groq-key-input');
    const val = input ? input.value.trim() : '';
    GROQ_API_KEY = val;
    localStorage.setItem('swiftmart_groq_key', val);
    const fb = document.getElementById('groq-feedback');
    if (fb) { fb.style.color = 'var(--fg)'; fb.textContent = 'Saving & verifying rates with Groq API...'; }
    await fetchRatesFromGroq();
    showToast(val ? 'Groq API Key saved successfully!' : 'Groq API Key cleared. Built-in rates active.');
    closeGroqKeyModal();
}

function clearGroqKey() {
    GROQ_API_KEY = '';
    localStorage.removeItem('swiftmart_groq_key');
    const input = document.getElementById('groq-key-input');
    if (input) input.value = '';
    showToast('Groq API Key cleared. Built-in rates active.');
    closeGroqKeyModal();
}


let products = [];
let cart = JSON.parse(localStorage.getItem('swiftmart_cart') || '[]');

// Delivery locations (Pakistan only): Country -> City -> Areas
const ADDRESS_DATA = {
    "Pakistan": {
        "Karachi": [
            "Clifton",
            "DHA",
            "Gulshan-e-Iqbal",
            "Gulistan-e-Johar",
            "North Nazimabad",
            "Nazimabad",
            "Saddar",
            "PECHS",
            "Bahadurabad",
            "Korangi",
            "Malir",
            "Federal B Area"
        ],
        "Lahore": [
            "Gulberg",
            "DHA",
            "Johar Town",
            "Model Town",
            "Bahria Town",
            "Iqbal Town",
            "Cantt",
            "Faisal Town",
            "Wapda Town"
        ],
        "Islamabad": [
            "F-6",
            "F-7",
            "F-8",
            "F-10",
            "F-11",
            "G-9",
            "G-10",
            "G-11",
            "I-8",
            "E-11",
            "Blue Area",
            "DHA",
            "Bahria Town"
        ],
        "Rawalpindi": [
            "Saddar",
            "Satellite Town",
            "Bahria Town",
            "Chaklala",
            "Westridge",
            "Commercial Market"
        ],
        "Faisalabad": [
            "Peoples Colony",
            "D Ground",
            "Madina Town",
            "Samanabad",
            "Gulberg"
        ],
        "Peshawar": [
            "University Town",
            "Hayatabad",
            "Saddar",
            "Cantt"
        ],
        "Multan": [
            "Cantt",
            "Gulgasht Colony",
            "Shah Rukn-e-Alam Colony"
        ],
        "Quetta": [
            "Satellite Town",
            "Jinnah Town",
            "Cantt"
        ]
    }
};
const PHONE_RULES = {
    "Pakistan": { re: /^(\+92|0)?3[0-9]{9}$/, hint: "0300 1234567 or +92 300 1234567" }
};
// City centers used to verify that the map pin is near the selected city
const CITY_CENTERS = {"Karachi": [24.8607, 67.0011], "Lahore": [31.5204, 74.3587], "Islamabad": [33.6844, 73.0479], "Rawalpindi": [33.5651, 73.0169], "Faisalabad": [31.4504, 73.135], "Peshawar": [34.0151, 71.5249], "Multan": [30.1978, 71.4697], "Quetta": [30.1798, 66.975]};
// Approximate centre and allowed radius for every area: [latitude, longitude, radius in km].
// The map pin must fall inside the circle of the selected area. Increase a radius if a valid address is rejected.
const AREA_CENTERS = {
    "Karachi": {
        "Clifton": [
            24.8138,
            67.03,
            4.0
        ],
        "DHA": [
            24.8,
            67.07,
            8.0
        ],
        "Gulshan-e-Iqbal": [
            24.9215,
            67.092,
            4.5
        ],
        "Gulistan-e-Johar": [
            24.918,
            67.13,
            4.5
        ],
        "North Nazimabad": [
            24.94,
            67.04,
            3.5
        ],
        "Nazimabad": [
            24.912,
            67.029,
            2.5
        ],
        "Saddar": [
            24.856,
            67.02,
            2.5
        ],
        "PECHS": [
            24.868,
            67.058,
            2.5
        ],
        "Bahadurabad": [
            24.883,
            67.068,
            2.0
        ],
        "Korangi": [
            24.83,
            67.13,
            6.0
        ],
        "Malir": [
            24.893,
            67.195,
            6.0
        ],
        "Federal B Area": [
            24.935,
            67.085,
            3.0
        ]
    },
    "Lahore": {
        "Gulberg": [
            31.512,
            74.345,
            3.5
        ],
        "DHA": [
            31.47,
            74.42,
            9.0
        ],
        "Johar Town": [
            31.469,
            74.277,
            3.5
        ],
        "Model Town": [
            31.484,
            74.322,
            3.0
        ],
        "Bahria Town": [
            31.36,
            74.185,
            6.0
        ],
        "Iqbal Town": [
            31.513,
            74.29,
            3.0
        ],
        "Cantt": [
            31.51,
            74.38,
            5.0
        ],
        "Faisal Town": [
            31.482,
            74.3,
            2.5
        ],
        "Wapda Town": [
            31.438,
            74.257,
            3.0
        ]
    },
    "Islamabad": {
        "F-6": [
            33.729,
            73.079,
            1.8
        ],
        "F-7": [
            33.72,
            73.056,
            1.8
        ],
        "F-8": [
            33.711,
            73.036,
            1.8
        ],
        "F-10": [
            33.696,
            73.01,
            1.8
        ],
        "F-11": [
            33.685,
            72.99,
            1.8
        ],
        "G-9": [
            33.692,
            73.035,
            1.8
        ],
        "G-10": [
            33.679,
            73.015,
            1.8
        ],
        "G-11": [
            33.668,
            72.995,
            1.8
        ],
        "I-8": [
            33.66,
            73.077,
            1.8
        ],
        "E-11": [
            33.7,
            72.98,
            2.0
        ],
        "Blue Area": [
            33.71,
            73.06,
            2.0
        ],
        "DHA": [
            33.545,
            73.16,
            7.0
        ],
        "Bahria Town": [
            33.52,
            73.11,
            6.0
        ]
    },
    "Rawalpindi": {
        "Saddar": [
            33.595,
            73.048,
            2.5
        ],
        "Satellite Town": [
            33.633,
            73.068,
            2.5
        ],
        "Bahria Town": [
            33.52,
            73.11,
            6.0
        ],
        "Chaklala": [
            33.609,
            73.098,
            3.0
        ],
        "Westridge": [
            33.59,
            73.017,
            2.5
        ],
        "Commercial Market": [
            33.613,
            73.07,
            1.5
        ]
    },
    "Faisalabad": {
        "Peoples Colony": [
            31.4,
            73.08,
            5.0
        ],
        "D Ground": [
            31.427,
            73.09,
            4.0
        ],
        "Madina Town": [
            31.405,
            73.06,
            5.0
        ],
        "Samanabad": [
            31.43,
            73.09,
            4.0
        ],
        "Gulberg": [
            31.43,
            73.05,
            5.0
        ]
    },
    "Peshawar": {
        "University Town": [
            34.005,
            71.485,
            3.0
        ],
        "Hayatabad": [
            33.99,
            71.44,
            5.0
        ],
        "Saddar": [
            34.009,
            71.542,
            2.5
        ],
        "Cantt": [
            34.0,
            71.55,
            4.0
        ]
    },
    "Multan": {
        "Cantt": [
            30.2008,
            71.4412,
            4.0
        ],
        "Gulgasht Colony": [
            30.2236,
            71.4909,
            3.0
        ],
        "Shah Rukn-e-Alam Colony": [
            30.2323,
            71.4417,
            3.5
        ]
    },
    "Quetta": {
        "Satellite Town": [
            30.16,
            66.99,
            4.0
        ],
        "Jinnah Town": [
            30.19,
            66.99,
            4.0
        ],
        "Cantt": [
            30.2,
            66.975,
            4.0
        ]
    }
};
const MAX_PIN_DISTANCE_KM = 60;
const PAKISTAN_BOUNDS = { minLat: 23.5, maxLat: 37.2, minLng: 60.8, maxLng: 77.9 };
function dlOptions(list) {
    return list.map(x => `<option value="${x}"></option>`).join('');
}
function addrMatch(list, v) {
    v = (v || '').trim().toLowerCase();
    return list.find(x => x.toLowerCase() === v) || '';
}

// ===== Delivery map pin (Leaflet + OpenStreetMap, loaded only when needed) =====
let deliveryPin = null;      // { lat, lng } chosen by the customer
let deliveryMap = null;
let deliveryMarker = null;
let areaZoneCircle = null;  // dashed circle showing where the pin must be
let pinPlace = null;         // place names found by reverse geocoding the pin, or null when unknown
let pinLookupId = 0;         // used to ignore outdated lookups
let lastLookupKey = '';      // last pin position that was looked up
let leafletLoading = null;

function loadLeaflet() {
    if (window.L) return Promise.resolve();
    if (leafletLoading) return leafletLoading;
    leafletLoading = new Promise((resolve, reject) => {
        const css = document.createElement('link');
        css.rel = 'stylesheet';
        css.href = 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css';
        document.head.appendChild(css);
        const script = document.createElement('script');
        script.src = 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js';
        script.onload = () => resolve();
        script.onerror = () => {
            leafletLoading = null;
            reject(new Error('The map could not be loaded. Please check your internet connection.'));
        };
        document.head.appendChild(script);
    });
    return leafletLoading;
}

function mapsLink(lat, lng) {
    return `https://www.google.com/maps?q=${lat},${lng}`;
}

function pinStatusHtml() {
    if (!deliveryPin) return 'No pin placed yet. Tap the map or use your current location.';
    const place = (pinPlace && pinPlace.names.length) ? ` &middot; Map shows: <b>${escapeHtml(pinPlace.names[0])}</b>` : '';
    return `Pin saved (${deliveryPin.lat}, ${deliveryPin.lng})${place} &middot; <a href="${mapsLink(deliveryPin.lat, deliveryPin.lng)}" target="_blank" rel="noopener" style="color:inherit;text-decoration:underline">Check on Google Maps</a>`;
}

function renderPinStatus() {
    const el = document.getElementById('pin-status');
    if (el) el.innerHTML = pinStatusHtml();
    const em = document.getElementById('pin-status-modal');
    if (em) em.innerHTML = pinStatusHtml();
}

// Normalises a place name so "Nazimabad Town" and "nazimabad" compare as equal.
function normPlace(s) {
    return String(s || '').toLowerCase()
        .replace(/\b(town|colony|block|sector|phase|society)\b/g, ' ')
        .replace(/[^a-z0-9]/g, '');
}

// Returns the area of this city that the map place names point to ('' when none is recognised).
function detectArea(city, placeNames) {
    const areas = Object.keys(AREA_CENTERS[city] || {});
    for (const raw of placeNames) {            // most specific name first
        const n = normPlace(raw);
        let best = '', bestLen = 0;
        for (const area of areas) {
            const a = normPlace(area);
            if (a && n.includes(a) && a.length > bestLen) { best = area; bestLen = a.length; }
        }
        if (best) return best;
    }
    return '';
}

// Asks OpenStreetMap which neighbourhood the pin is in. If this fails (offline), the distance check still applies.
async function lookupPinPlace(lat, lng) {
    const myId = ++pinLookupId;
    let result = null;
    try {
        const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&zoom=16&accept-language=en&lat=${lat}&lon=${lng}`;
        const res = await fetch(url);
        if (!res.ok) throw new Error('Lookup failed');
        const data = await res.json();
        const a = data.address || {};
        result = { names: [a.suburb, a.neighbourhood, a.quarter, a.residential].filter(Boolean) };
    } catch (e) {
        result = null;
    }
    if (myId !== pinLookupId) return;           // a newer pin was placed meanwhile
    pinPlace = result;
    renderPinStatus();
    validateDeliveryPin(false);
}

function distanceKm(lat1, lng1, lat2, lng2) {
    const rad = d => d * Math.PI / 180;
    const a = Math.sin(rad(lat2 - lat1) / 2) ** 2 +
        Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(rad(lng2 - lng1) / 2) ** 2;
    return 6371 * 2 * Math.asin(Math.sqrt(a));
}

function selectedAreaZone() {
    const cityEl = document.getElementById('f-city');
    const areaEl = document.getElementById('f-area');
    if (!cityEl || !areaEl) return null;
    const city = addrMatch(Object.keys(AREA_CENTERS), cityEl.value);
    const areas = city ? AREA_CENTERS[city] : null;
    const area = areas ? addrMatch(Object.keys(areas), areaEl.value) : '';
    return area ? { city, area, lat: areas[area][0], lng: areas[area][1], radius: areas[area][2] } : null;
}

function selectedCityCenter() {
    const cityEl = document.getElementById('f-city');
    const city = cityEl ? addrMatch(Object.keys(CITY_CENTERS), cityEl.value) : '';
    return city ? { city, center: CITY_CENTERS[city] } : null;
}

function validateDeliveryPin(strict) {
    const err = document.getElementById('err-pin');
    let msg = '';
    if (!deliveryPin) {
        if (strict) msg = 'Please drop a pin on the map or use your current location.';
    } else if (deliveryPin.lat < PAKISTAN_BOUNDS.minLat || deliveryPin.lat > PAKISTAN_BOUNDS.maxLat ||
        deliveryPin.lng < PAKISTAN_BOUNDS.minLng || deliveryPin.lng > PAKISTAN_BOUNDS.maxLng) {
        msg = 'The pin must be inside Pakistan.';
    } else {
        const zone = selectedAreaZone();
        if (zone) {
            if (distanceKm(deliveryPin.lat, deliveryPin.lng, zone.lat, zone.lng) > zone.radius) {
                msg = `Your pin is not inside ${zone.area}, ${zone.city}. Move the pin or choose the correct area.`;
            } else if (pinPlace) {
                const detected = detectArea(zone.city, pinPlace.names);
                if (detected && detected !== zone.area) {
                    msg = `The map shows this spot is in ${detected}, not ${zone.area}. Move the pin or choose the correct area.`;
                }
            }
        } else {
            const sel = selectedCityCenter();
            if (sel && distanceKm(deliveryPin.lat, deliveryPin.lng, sel.center[0], sel.center[1]) > MAX_PIN_DISTANCE_KM) {
                msg = `Your pin is too far from ${sel.city}. Move the pin or change the city.`;
            }
        }
    }
    if (err) err.textContent = msg;
    return !msg;
}

function setDeliveryPin(lat, lng, pan) {
    deliveryPin = { lat: Number(lat.toFixed(6)), lng: Number(lng.toFixed(6)) };
    if (deliveryMap && window.L) {
        if (!deliveryMarker) {
            const icon = L.divIcon({
                className: '',
                html: '<div style="width:18px;height:18px;border-radius:50%;background:#e11d48;border:3px solid #fff;box-shadow:0 0 0 2px #e11d48"></div>',
                iconSize: [24, 24],
                iconAnchor: [12, 12]
            });
            deliveryMarker = L.marker([lat, lng], { draggable: true, icon }).addTo(deliveryMap);
            deliveryMarker.on('dragend', () => {
                const p = deliveryMarker.getLatLng();
                setDeliveryPin(p.lat, p.lng, false);
            });
        } else {
            deliveryMarker.setLatLng([lat, lng]);
        }
        if (pan) deliveryMap.setView([lat, lng], 17);
    }
    const key = `${deliveryPin.lat},${deliveryPin.lng}`;
    if (key !== lastLookupKey) {
        lastLookupKey = key;
        pinPlace = null;
        lookupPinPlace(deliveryPin.lat, deliveryPin.lng);
    }
    renderPinStatus();
    validateDeliveryPin(false);
}

function closeMapModal() {
    const m = document.getElementById('map-modal');
    if (deliveryMap) { deliveryMap.remove(); }
    deliveryMap = null; deliveryMarker = null; areaZoneCircle = null;
    if (m) m.remove();
    document.body.style.overflow = '';
    renderPinStatus();
}

async function toggleDeliveryMap() {
    const err = document.getElementById('err-pin');
    if (document.getElementById('map-modal')) return;
    try {
        await loadLeaflet();
    } catch (e) {
        if (err) err.textContent = e.message;
        return;
    }
    const modal = document.createElement('div');
    modal.id = 'map-modal';
    modal.innerHTML = `
    <div class="mm-head">
      <div>
        <div class="mm-title">Pin your delivery location</div>
        <div class="mm-sub">Tap the map to drop a pin, or drag the pin to adjust.</div>
      </div>
      <button type="button" class="mm-close" aria-label="Close map" onclick="closeMapModal()">&times;</button>
    </div>
    <div class="mm-search">
      <form class="mm-search-row" onsubmit="searchMapPlace(event)">
        <input type="search" id="map-search-input" placeholder="Search area, street or landmark in Pakistan" autocomplete="off" oninput="onMapSearchInput()">
        <button type="submit" class="btn" id="map-search-btn">Search</button>
      </form>
      <div id="map-search-results" class="mm-results"></div>
    </div>
    <div id="delivery-map" class="mm-map"></div>
    <div class="mm-foot">
      <div id="pin-status-modal" class="mm-status"></div>
      <div class="mm-actions">
        <button type="button" class="btn alt" onclick="useMyLocation()">Use my location</button>
        <button type="button" class="btn" onclick="closeMapModal()">Confirm location</button>
      </div>
    </div>`;
    document.body.appendChild(modal);
    document.body.style.overflow = 'hidden';
    const box = document.getElementById('delivery-map');
    const zone = selectedAreaZone();
    const sel = selectedCityCenter();
    const start = deliveryPin ? [deliveryPin.lat, deliveryPin.lng] : (zone ? [zone.lat, zone.lng] : (sel ? sel.center : [30.3753, 69.3451]));
    const zoom = deliveryPin ? 17 : (zone ? 14 : (sel ? 12 : 5));
    deliveryMap = L.map(box, { zoomControl: true }).setView(start, zoom);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; OpenStreetMap contributors'
    }).addTo(deliveryMap);
    deliveryMap.on('click', ev => setDeliveryPin(ev.latlng.lat, ev.latlng.lng, false));
    if (deliveryPin) setDeliveryPin(deliveryPin.lat, deliveryPin.lng, false);
    updateAreaZone();
    renderPinStatus();
    setTimeout(() => deliveryMap && deliveryMap.invalidateSize(), 50);
}

function updateAreaZone() {
    if (!deliveryMap || !window.L) return;
    if (areaZoneCircle) { deliveryMap.removeLayer(areaZoneCircle); areaZoneCircle = null; }
    const zone = selectedAreaZone();
    if (zone) {
        areaZoneCircle = L.circle([zone.lat, zone.lng], {
            radius: zone.radius * 1000, color: '#e11d48', weight: 1.5, dashArray: '6 6', fillOpacity: 0.06
        }).addTo(deliveryMap);
    }
}

function recenterMapToCity() {
    if (deliveryMap && !deliveryPin) {
        const zone = selectedAreaZone();
        const sel = selectedCityCenter();
        if (zone) deliveryMap.setView([zone.lat, zone.lng], 14);
        else if (sel) deliveryMap.setView(sel.center, 12);
    }
    updateAreaZone();
}

// Search box on the map: asks our Java server (/api/geocode), which asks OpenStreetMap. Pakistan only.
let mapSearchHits = [];
let mapSearchTimer = null;
let mapSearchSeq = 0;

function onMapSearchInput() {
    clearTimeout(mapSearchTimer);
    const q = document.getElementById('map-search-input').value.trim();
    const box = document.getElementById('map-search-results');
    if (q.length < 3) { if (box) box.innerHTML = ''; return; }
    mapSearchTimer = setTimeout(() => runMapSearch(false), 700);   // suggestions while typing
}

// The area to search in: the customer's delivery city, otherwise whatever the map is showing now.
function searchArea() {
    const sel = selectedCityCenter();
    let lat, lng;
    if (sel) { lat = sel.center[0]; lng = sel.center[1]; }
    else if (deliveryMap) { const c = deliveryMap.getCenter(); lat = c.lat; lng = c.lng; }
    else return '';
    const d = 0.4;   // about 40 km each way
    return [lng - d, lat + d, lng + d, lat - d].map(n => n.toFixed(4)).join(',');   // minLng,maxLat,maxLng,minLat
}

async function fetchPlaces(q, vb, bounded) {
    let extra = '';
    if (vb) extra = '&vb=' + vb + (bounded ? '&bounded=1' : '');
    try {
        const res = await fetch('/api/geocode?q=' + encodeURIComponent(q) + extra);
        if (!res.ok) throw new Error('server');
        return await res.json();
    } catch (e) {
        // Fallback if the Java server was not restarted with the new code
        const direct = vb ? '&viewbox=' + vb + (bounded ? '&bounded=1' : '') : '';
        const res = await fetch('https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&countrycodes=pk&accept-language=en&q=' + encodeURIComponent(q) + direct);
        if (!res.ok) throw new Error('Search failed');
        return await res.json();
    }
}

// Like Google Maps: look in the delivery city first, then nearby, then all of Pakistan.
async function findPlaces(q) {
    const vb = searchArea();
    let hits = [];
    if (vb) hits = await fetchPlaces(q, vb, true);
    if (!hits.length && vb) hits = await fetchPlaces(q, vb, false);
    if (!hits.length) hits = await fetchPlaces(q, '', false);
    return hits;
}

async function runMapSearch(jumpToFirst) {
    const input = document.getElementById('map-search-input');
    const box = document.getElementById('map-search-results');
    if (!input || !box) return;
    const q = input.value.trim();
    if (q.length < 3) { box.innerHTML = '<div class="mm-res-msg">Type at least 3 characters.</div>'; return; }
    const mySeq = ++mapSearchSeq;
    box.innerHTML = '<div class="mm-res-msg">Searching...</div>';
    try {
        const hits = await findPlaces(q);
        if (mySeq !== mapSearchSeq) return;          // a newer search started
        mapSearchHits = hits;
        if (!hits.length) {
            box.innerHTML = '<div class="mm-res-msg">No places found. Try a different spelling or a nearby landmark.</div>';
        } else if (jumpToFirst) {
            pickSearchResult(0);                     // Enter / Search button: open the best match on the map
        } else {
            box.innerHTML = hits.map((h, i) =>
                `<button type="button" class="mm-res" onclick="pickSearchResult(${i})">${escapeHtml(h.display_name)}</button>`).join('');
        }
    } catch (e) {
        if (mySeq !== mapSearchSeq) return;
        box.innerHTML = '<div class="mm-res-msg">Search is unavailable right now. Check your connection or tap the map instead.</div>';
    }
}

function searchMapPlace(ev) {
    if (ev) ev.preventDefault();
    clearTimeout(mapSearchTimer);
    runMapSearch(true);
}

function pickSearchResult(i) {
    const h = mapSearchHits[i];
    if (!h) return;
    setDeliveryPin(parseFloat(h.lat), parseFloat(h.lon), true);
    const box = document.getElementById('map-search-results');
    if (box) box.innerHTML = '';
}

async function useMyLocation() {
    const err = document.getElementById('err-pin');
    if (!navigator.geolocation) {
        if (err) err.textContent = 'Your browser does not support location access.';
        return;
    }
    if (err) err.textContent = '';
    navigator.geolocation.getCurrentPosition(
        async pos => {
            if (!document.getElementById('map-modal')) await toggleDeliveryMap();
            setDeliveryPin(pos.coords.latitude, pos.coords.longitude, true);
        },
        () => {
            if (err) err.textContent = 'Could not get your location. Allow location access or drop a pin on the map.';
        },
        { enableHighAccuracy: true, timeout: 15000 }
    );
}
let activeCat = 'All';
let searchQuery = '';
let sortMode = '';
let currentModalProduct = null;
let modalQty = 1;
let modalColor = '';
let modalSize = '';
let sliderTimer = null;
let currentSlide = 0;
let selectedReviewStars = 5;

// Guaranteed Real High-Resolution Studio Product Photography
const REAL_PRODUCT_IMAGES = {
    // Electronics
    'Wireless Headphones': 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=85',
    'Gaming Laptop': 'https://images.unsplash.com/photo-1603302576837-37561b2e2302?auto=format&fit=crop&w=800&q=85',
    'Smartphone Pro': 'https://images.unsplash.com/photo-1598327105666-5b89351aff97?auto=format&fit=crop&w=800&q=85',
    'Bluetooth Speaker': 'https://images.unsplash.com/photo-1545454675-3531b543be5d?auto=format&fit=crop&w=800&q=85',
    '4K Action Camera': 'https://images.unsplash.com/photo-1565849904461-04a58ad377e0?auto=format&fit=crop&w=800&q=85',
    'Noise Cancelling Earbuds': 'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?auto=format&fit=crop&w=800&q=85',
    'Ultra Tablet Pro': 'https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=800&q=85',
    'Mechanical Gaming Keyboard': 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=800&q=85',
    'Wireless Ergonomic Mouse': 'https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?auto=format&fit=crop&w=800&q=85',
    'Fast Power Bank 20000mAh': 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?auto=format&fit=crop&w=800&q=85',
    'Portable SSD 1TB': 'https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=800&q=85',
    'Mirrorless 4K Camera': 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=800&q=85',
    'Smart OLED Fitness Band': 'https://images.unsplash.com/photo-1575311373937-040b8e1fd5b6?auto=format&fit=crop&w=800&q=85',
    'Compact Studio Soundbar': 'https://images.unsplash.com/photo-1545454675-3531b543be5d?auto=format&fit=crop&w=800&q=85',
    'Smart Home Assistant Hub': 'https://images.unsplash.com/photo-1543512214-318c7553f230?auto=format&fit=crop&w=800&q=85',

    // Clothing
    'Classic Hoodie': 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?auto=format&fit=crop&w=800&q=85',
    'Slim Fit Jeans': 'https://images.unsplash.com/photo-1542272604-780c96856592?auto=format&fit=crop&w=800&q=85',
    'Executive Suit': 'https://images.unsplash.com/photo-1594938298603-c8148c4dae35?auto=format&fit=crop&w=800&q=85',
    'Cotton T-Shirt': 'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?auto=format&fit=crop&w=800&q=85',
    'Denim Jacket': 'https://images.unsplash.com/photo-1576995853123-5a10305d93c0?auto=format&fit=crop&w=800&q=85',
    'Oversized Fleece Sweatshirt': 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?auto=format&fit=crop&w=800&q=85',
    'Tailored Chino Trousers': 'https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?auto=format&fit=crop&w=800&q=85',
    'Classic Wool Trench Coat': 'https://images.unsplash.com/photo-1539533018447-63fcce667823?auto=format&fit=crop&w=800&q=85',
    'Flight Bomber Jacket': 'https://images.unsplash.com/photo-1548883354-7622d03aca27?auto=format&fit=crop&w=800&q=85',
    'Casual Oxford Shirt': 'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?auto=format&fit=crop&w=800&q=85',
    'Merino Wool Turtleneck': 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?auto=format&fit=crop&w=800&q=85',
    'Linen Summer Button-Down': 'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?auto=format&fit=crop&w=800&q=85',
    'Utility Cargo Pants': 'https://images.unsplash.com/photo-1517445312882-bc9910d016b7?auto=format&fit=crop&w=800&q=85',
    'Athletic Track Jacket': 'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?auto=format&fit=crop&w=800&q=85',
    'Quilted Puffer Vest': 'https://images.unsplash.com/photo-1544022613-e87ca75a784a?auto=format&fit=crop&w=800&q=85',

    // Shoes
    'Running Sneakers': 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=800&q=85',
    'Leather Boots': 'https://images.unsplash.com/photo-1520639888713-7851133b1ed0?auto=format&fit=crop&w=800&q=85',
    'Classic Loafers': 'https://images.unsplash.com/photo-1614252235316-8c857d38b5f4?auto=format&fit=crop&w=800&q=85',
    'Air Basketball High-Tops': 'https://images.unsplash.com/photo-1552346154-21d32810aba3?auto=format&fit=crop&w=800&q=85',
    'Canvas Slip-On Sneakers': 'https://images.unsplash.com/photo-1525966222134-fcfa99b8ae77?auto=format&fit=crop&w=800&q=85',
    'Oxford Formal Dress Shoes': 'https://images.unsplash.com/photo-1478180170835-77987b5a198c?auto=format&fit=crop&w=800&q=85',
    'Trail Hiking Mountain Boots': 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=800&q=85',
    'Chelsea Suede Boots': 'https://images.unsplash.com/photo-1638247025967-b4e38f787b76?auto=format&fit=crop&w=800&q=85',
    'Minimalist Leather Tennis Shoes': 'https://images.unsplash.com/photo-1560769629-975ec94e6a86?auto=format&fit=crop&w=800&q=85',
    'Suede Derby Shoes': 'https://images.unsplash.com/photo-1533867617858-e7b97e060509?auto=format&fit=crop&w=800&q=85',
    'Lightweight Training Runners': 'https://images.unsplash.com/photo-1460353581641-37baddab0fa2?auto=format&fit=crop&w=800&q=85',
    'Double Monk Strap Shoes': 'https://images.unsplash.com/photo-1595950653106-6c9ebd614d3a?auto=format&fit=crop&w=800&q=85',
    'Retro Suede Skate Sneakers': 'https://images.unsplash.com/photo-1539185441755-769473a23570?auto=format&fit=crop&w=800&q=85',
    'Summer Canvas Espadrilles': 'https://images.unsplash.com/photo-1562183241-b937e95585b6?auto=format&fit=crop&w=800&q=85',
    'Slip-On Leather Driving Shoes': 'https://images.unsplash.com/photo-1582588678413-dbf45f4823e9?auto=format&fit=crop&w=800&q=85',

    // Bags
    'Leather Backpack': 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=800&q=85',
    'Travel Duffel': 'https://images.unsplash.com/photo-1544816155-12df9643f363?auto=format&fit=crop&w=800&q=85',
    'Minimalist Crossbody Bag': 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?auto=format&fit=crop&w=800&q=85',
    'Waterproof Commuter Backpack': 'https://images.unsplash.com/photo-1622560480605-d83c853bc5c3?auto=format&fit=crop&w=800&q=85',
    'Waxed Canvas Messenger Bag': 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=800&q=85',
    'Rolling Carry-On Luggage': 'https://images.unsplash.com/photo-1565026057447-bc90a3dceb87?auto=format&fit=crop&w=800&q=85',
    'Gym Sports Duffle with Shoe Pocket': 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=800&q=85',
    'Vintage Leather Satchel': 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?auto=format&fit=crop&w=800&q=85',
    'Everyday Heavyweight Tote Bag': 'https://images.unsplash.com/photo-1590874103328-eac38a683ce7?auto=format&fit=crop&w=800&q=85',
    'Tactical Molle Daypack': 'https://images.unsplash.com/photo-1577733966973-d680bffd2e80?auto=format&fit=crop&w=800&q=85',
    'Slim RFID Leather Cardholder': 'https://images.unsplash.com/photo-1627123424574-724758594e93?auto=format&fit=crop&w=800&q=85',
    'Camera Shoulder Bag': 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?auto=format&fit=crop&w=800&q=85',
    'Weekender Overnighter Bag': 'https://images.unsplash.com/photo-1544816155-12df9643f363?auto=format&fit=crop&w=800&q=85',
    'Urban Hip Belt Pack': 'https://images.unsplash.com/photo-1544816155-12df9643f363?auto=format&fit=crop&w=800&q=85',
    'Expandable 40L Travel Pack': 'https://images.unsplash.com/photo-1622560480605-d83c853bc5c3?auto=format&fit=crop&w=800&q=85',

    // Watches
    'Minimal Watch': 'https://images.unsplash.com/photo-1524805444758-089113d48a6d?auto=format&fit=crop&w=800&q=85',
    'Chronograph': 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?auto=format&fit=crop&w=800&q=85',
    'Automatic Aviator Pilot Watch': 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=800&q=85',
    'Diver 200m Automatic': 'https://images.unsplash.com/photo-1547996160-71dfabb19286?auto=format&fit=crop&w=800&q=85',
    'Skeleton Mechanical Timepiece': 'https://images.unsplash.com/photo-1508685096489-7aacd43bd3b1?auto=format&fit=crop&w=800&q=85',
    'Vintage Retro Digital Watch': 'https://images.unsplash.com/photo-1524805444758-089113d48a6d?auto=format&fit=crop&w=800&q=85',
    'Solar Powered Field Watch': 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?auto=format&fit=crop&w=800&q=85',
    'Bauhaus Minimalist Dress Watch': 'https://images.unsplash.com/photo-1533139502658-0198f920d8e8?auto=format&fit=crop&w=800&q=85',
    'Ceramic Bezel Sports Watch': 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?auto=format&fit=crop&w=800&q=85',
    'Titanium Lightweight Watch': 'https://images.unsplash.com/photo-1524592094714-0f0654e20314?auto=format&fit=crop&w=800&q=85',
    'Stainless Steel Bracelet Watch': 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?auto=format&fit=crop&w=800&q=85',
    'World Time GMT Watch': 'https://images.unsplash.com/photo-1547996160-71dfabb19286?auto=format&fit=crop&w=800&q=85',
    'Smart Hybrid Watch': 'https://images.unsplash.com/photo-1579586337278-3befd40fd17a?auto=format&fit=crop&w=800&q=85',
    'Luxury Gold Plated Slim Watch': 'https://images.unsplash.com/photo-1524805444758-089113d48a6d?auto=format&fit=crop&w=800&q=85',
    'Military Tactical Watch': 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?auto=format&fit=crop&w=800&q=85',

    // Groceries
    'Organic Coffee': 'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=800&q=85',
    'Dark Chocolate Box': 'https://images.unsplash.com/photo-1549007994-cb92caebd54b?auto=format&fit=crop&w=800&q=85',
    'Green Tea Pack': 'https://images.unsplash.com/photo-1576092768241-dec231879fc3?auto=format&fit=crop&w=800&q=85',
    'Raw Mountain Honey 500g': 'https://images.unsplash.com/photo-1587049352846-4a222e784d38?auto=format&fit=crop&w=800&q=85',
    'Extra Virgin Olive Oil 1L': 'https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?auto=format&fit=crop&w=800&q=85',
    'Whole Grain Toasted Granola': 'https://images.unsplash.com/photo-1517093707567-335b376d3f27?auto=format&fit=crop&w=800&q=85',
    'Creamy Almond Butter 400g': 'https://images.unsplash.com/photo-1590080875515-8a3a8dc5735e?auto=format&fit=crop&w=800&q=85',
    'Himalayan Pink Salt Grinder': 'https://images.unsplash.com/photo-1615485500704-8e990f9900f7?auto=format&fit=crop&w=800&q=85',
    'Organic Ceremonial Matcha Tea': 'https://images.unsplash.com/photo-1536256263959-770b48d82b0a?auto=format&fit=crop&w=800&q=85',
    'Organic Tricolor Quinoa 1kg': 'https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=800&q=85',
    'Sun-Dried Medjool Dates 500g': 'https://images.unsplash.com/photo-1594282486552-05b4d80fbb9f?auto=format&fit=crop&w=800&q=85',
    'Artisan Bronze Cut Pasta': 'https://images.unsplash.com/photo-1551462147-ff29053bfc14?auto=format&fit=crop&w=800&q=85',
    'Dark Roast Espresso Whole Beans': 'https://images.unsplash.com/photo-1587734195503-904fca47e0e9?auto=format&fit=crop&w=800&q=85',
    'Pure Maple Syrup Grade A 250ml': 'https://images.unsplash.com/photo-1589301760014-d929f3979dbc?auto=format&fit=crop&w=800&q=85',
    'Natural Organic Chia Seeds 500g': 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?auto=format&fit=crop&w=800&q=85',

    // Stationery
    'Premium Notebook': 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=800&q=85',
    'Fountain Pen': 'https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?auto=format&fit=crop&w=800&q=85',
    'Brass Heavyweight Mechanical Pencil': 'https://images.unsplash.com/photo-1585336261026-c2a0b1fba50a?auto=format&fit=crop&w=800&q=85',
    'Hardcover Watercolor Sketchbook': 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=800&q=85',
    'Archival Calligraphy Ink Bottle': 'https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?auto=format&fit=crop&w=800&q=85',
    'Fine Gel Pens 0.5mm 10-Pack': 'https://images.unsplash.com/photo-1585336261026-c2a0b1fba50a?auto=format&fit=crop&w=800&q=85',
    'Handcrafted Leather Journal Cover': 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=800&q=85',
    'Solid Aluminium Precision Ruler Set': 'https://images.unsplash.com/photo-1585336261026-c2a0b1fba50a?auto=format&fit=crop&w=800&q=85',
    'Eco Vegan Leather Desk Mat': 'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?auto=format&fit=crop&w=800&q=85',
    'Pastel Minimalist Sticky Notes Pack': 'https://images.unsplash.com/photo-1586075010923-2dd4570fb338?auto=format&fit=crop&w=800&q=85',
    'Solid Brass Bookmark Clip Set': 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=800&q=85',
    'Executive Matte Letter Opener': 'https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?auto=format&fit=crop&w=800&q=85',
    'Expandable Accordion Document Folder': 'https://images.unsplash.com/photo-1586075010923-2dd4570fb338?auto=format&fit=crop&w=800&q=85',
    'Micro Fine Liner Artist Pens 6-Pack': 'https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?auto=format&fit=crop&w=800&q=85',
    'Solid Walnut Wood Desk Pen Stand': 'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?auto=format&fit=crop&w=800&q=85',

    // Toys
    'Building Blocks Set': 'https://images.unsplash.com/photo-1587654780291-39c9404d746b?auto=format&fit=crop&w=800&q=85',
    'RC Racing Car': 'https://images.unsplash.com/photo-1594787318286-3d835c1d207f?auto=format&fit=crop&w=800&q=85',
    'Mini Folding Camera Drone': 'https://images.unsplash.com/photo-1507582020474-9a35b7d455d9?auto=format&fit=crop&w=800&q=85',
    'Tournament Handcrafted Wooden Chess': 'https://images.unsplash.com/photo-1529699211952-734e80c4d42b?auto=format&fit=crop&w=800&q=85',
    '3D Magnetic Tiles 100-Piece Kit': 'https://images.unsplash.com/photo-1587654780291-39c9404d746b?auto=format&fit=crop&w=800&q=85',
    'Diecast 1:18 Model Supercar': 'https://images.unsplash.com/photo-1594787318286-3d835c1d207f?auto=format&fit=crop&w=800&q=85',
    'Classic Soft Plush Bear 40cm': 'https://images.unsplash.com/photo-1559454403-b8fb88521f11?auto=format&fit=crop&w=800&q=85',
    'Monocular Compound Science Microscope': 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=800&q=85',
    'Magnetic Competition Speed Cube': 'https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?auto=format&fit=crop&w=800&q=85',
    'Programmable Educational Robot': 'https://images.unsplash.com/photo-1485827404703-89b55fcc595e?auto=format&fit=crop&w=800&q=85',
    'Classic Wooden Railway Train Set': 'https://images.unsplash.com/photo-1587654780291-39c9404d746b?auto=format&fit=crop&w=800&q=85',
    'Deluxe Artist Easel & Acrylics Set': 'https://images.unsplash.com/photo-1513364776144-60967b0f800f?auto=format&fit=crop&w=800&q=85',
    'Gyroscope RC Indoor Helicopter': 'https://images.unsplash.com/photo-1507582020474-9a35b7d455d9?auto=format&fit=crop&w=800&q=85',
    '1000-Piece Panoramic Art Jigsaw': 'https://images.unsplash.com/photo-1587654780291-39c9404d746b?auto=format&fit=crop&w=800&q=85',
    'Collectible Anime Action Figure': 'https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?auto=format&fit=crop&w=800&q=85',

    // Furniture
    'Ergonomic Chair': 'https://images.unsplash.com/photo-1580481077195-c328a37db714?auto=format&fit=crop&w=800&q=85',
    'Standing Desk': 'https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?auto=format&fit=crop&w=800&q=85',
    'Table Lamp': 'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=800&q=85',
    'Solid Oak Minimalist Coffee Table': 'https://images.unsplash.com/photo-1533090161767-e6ffed986c88?auto=format&fit=crop&w=800&q=85',
    'Velvet Accent Lounge Armchair': 'https://images.unsplash.com/photo-1586023492125-27b2c045efd7?auto=format&fit=crop&w=800&q=85',
    '5-Tier Modular Wall Bookshelf': 'https://images.unsplash.com/photo-1594671581654-278545cfdb80?auto=format&fit=crop&w=800&q=85',
    'Bedside Dual Drawer Nightstand': 'https://images.unsplash.com/photo-1532323544230-7191fd51bc1b?auto=format&fit=crop&w=800&q=85',
    'Floating Wall Shelves 3-Piece': 'https://images.unsplash.com/photo-1594671581654-278545cfdb80?auto=format&fit=crop&w=800&q=85',
    'Modern Arc Steel Floor Lamp': 'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=800&q=85',
    'Contemporary 3-Seater Fabric Sofa': 'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=800&q=85',
    'Solid Hardwood Dining Table': 'https://images.unsplash.com/photo-1615066390971-03e4e1c36ddf?auto=format&fit=crop&w=800&q=85',
    'Top-Grain Leather Swivel Recliner': 'https://images.unsplash.com/photo-1586023492125-27b2c045efd7?auto=format&fit=crop&w=800&q=85',
    'Ceramic Indoor Planter with Stand': 'https://images.unsplash.com/photo-1485955900006-10f4d324d411?auto=format&fit=crop&w=800&q=85',
    'Adjustable Modern Swivel Bar Stool': 'https://images.unsplash.com/photo-1503602642458-232111445657?auto=format&fit=crop&w=800&q=85',
    'Solid Ash Wood Coat Hat Rack': 'https://images.unsplash.com/photo-1532323544230-7191fd51bc1b?auto=format&fit=crop&w=800&q=85'
};

const CATEGORY_FALLBACK_IMAGES = {
    'Electronics': 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=85',
    'Clothing': 'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?auto=format&fit=crop&w=800&q=85',
    'Shoes': 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=800&q=85',
    'Bags': 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=800&q=85',
    'Watches': 'https://images.unsplash.com/photo-1524805444758-089113d48a6d?auto=format&fit=crop&w=800&q=85',
    'Groceries': 'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=800&q=85',
    'Stationery': 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=800&q=85',
    'Toys': 'https://images.unsplash.com/photo-1587654780291-39c9404d746b?auto=format&fit=crop&w=800&q=85',
    'Furniture': 'https://images.unsplash.com/photo-1580481077195-c328a37db714?auto=format&fit=crop&w=800&q=85'
};

function getProductImage(p) {
    if (p.img && p.img !== '-' && p.img.length > 5) return p.img;
    if (REAL_PRODUCT_IMAGES[p.name]) return REAL_PRODUCT_IMAGES[p.name];
    return CATEGORY_FALLBACK_IMAGES[p.cat] || 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=85';
}

// Vector Fallback in case of network drop
function getProductFallbackSvg(cat) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="100%" height="100%">
    <rect width="100" height="100" fill="#f4f4f4"/>
    <text x="50" y="55" fill="#111" font-size="12" font-family="sans-serif" font-weight="800" text-anchor="middle">${cat.toUpperCase()}</text>
  </svg>`;
    return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
}

// --- Initialization ---
async function init() {
    setupTheme();
    setupListeners();
    updateCartBadge();
    const currSel = document.getElementById('currency-select');
    if (currSel) currSel.value = currentCurrency;
    await loadCatalog();
    fetchRatesFromGroq();
}

function setupTheme() {
    const saved = localStorage.getItem('swiftmart_theme') || 'light';
    document.documentElement.setAttribute('data-theme', saved);
    updateThemeButton(saved);

    const btn = document.getElementById('theme');
    if (btn) {
        btn.onclick = () => {
            const cur = document.documentElement.getAttribute('data-theme') || 'light';
            const next = cur === 'dark' ? 'light' : 'dark';
            document.documentElement.setAttribute('data-theme', next);
            localStorage.setItem('swiftmart_theme', next);
            updateThemeButton(next);
        };
    }
}

function updateThemeButton(theme) {
    const btn = document.getElementById('theme');
    if (btn) btn.textContent = theme === 'dark' ? '☀ Light' : '☾ Dark';
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/[&<>"']/g, m => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    })[m]);
}

function escapeQuote(str) {
    if (!str) return '';
    return String(str).replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

function setupListeners() {
    const q = document.getElementById('q');
    if (q) {
        q.addEventListener('input', (e) => {
            searchQuery = e.target.value.toLowerCase().trim();
            render();
        });
    }

    const sort = document.getElementById('sort');
    if (sort) {
        sort.addEventListener('change', (e) => {
            sortMode = e.target.value;
            render();
        });
    }
}

let reviewsByProduct = {};

async function loadCatalog() {
    try {
        const [pRes, rRes] = await Promise.all([
            fetch('/api/products'),
            fetch('/api/reviews').catch(() => null)
        ]);
        if (!pRes.ok) throw new Error('Catalog service unavailable');
        products = await pRes.json();

        if (rRes && rRes.ok) {
            try {
                const allReviews = await rRes.json();
                reviewsByProduct = {};
                allReviews.forEach(r => {
                    const pid = Number(r.productId || r.id);
                    if (!reviewsByProduct[pid]) reviewsByProduct[pid] = [];
                    reviewsByProduct[pid].push(r);
                });
            } catch (ignored) { }
        }

        renderCategories();
        render();
    } catch (err) {
        document.getElementById('main').innerHTML = `
      <div class="empty">
        <h3>Could not connect to SwiftMart server</h3>
        <p style="margin-top:8px">Please ensure <code>Main.java</code> is running.</p>
      </div>`;
    }
}

function getProductRatingInfo(productId) {
    const revs = reviewsByProduct[productId] || [];
    if (revs.length === 0) {
        return {
            count: 0,
            avg: 0,
            starsHtml: `<span style="color:var(--bd);letter-spacing:1px">☆☆☆☆☆</span>`,
            text: `No reviews yet`
        };
    }
    const sum = revs.reduce((acc, r) => acc + (Number(r.stars) || 5), 0);
    const avg = sum / revs.length;
    const rounded = Math.round(avg);
    const stars = '★'.repeat(rounded) + '☆'.repeat(5 - rounded);
    return {
        count: revs.length,
        avg: avg.toFixed(1),
        starsHtml: `<span class="review-stars">${stars}</span>`,
        text: `${avg.toFixed(1)} (${revs.length})`
    };
}

function renderCategories() {
    const nav = document.getElementById('chips');
    if (!nav) return;
    const cats = ['All', ...new Set(products.map(p => p.cat))];
    nav.innerHTML = cats.map(c => `
    <button class="${c === activeCat ? 'on' : ''}" onclick="selectCategory('${c}')">${c}</button>
  `).join('');
}

function selectCategory(cat) {
    activeCat = cat;
    renderCategories();
    render();
}

function home() {
    activeCat = 'All';
    searchQuery = '';
    const q = document.getElementById('q');
    if (q) q.value = '';
    renderCategories();
    render();
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function getFilteredProducts() {
    let list = [...products];
    if (activeCat !== 'All') {
        list = list.filter(p => p.cat.toLowerCase() === activeCat.toLowerCase());
    }
    if (searchQuery) {
        list = list.filter(p =>
            p.name.toLowerCase().includes(searchQuery) ||
            p.brand.toLowerCase().includes(searchQuery) ||
            p.cat.toLowerCase().includes(searchQuery)
        );
    }
    if (sortMode === 'lo') list.sort((a, b) => a.final - b.final);
    else if (sortMode === 'hi') list.sort((a, b) => b.final - a.final);
    else if (sortMode === 'az') list.sort((a, b) => a.name.localeCompare(b.name));
    return list;
}

// --- Storefront Rendering ---
function render() {
    const main = document.getElementById('main');
    if (!main) return;

    const items = getFilteredProducts();

    let heroHtml = '';
    if (activeCat === 'All' && !searchQuery) {
        heroHtml = `
      <div class="hero-slider-wrap" id="hero-slider-wrap">
        <div class="hero-slider" id="hero-slider">
          <!-- Slide 1 -->
          <div class="slide" style="background-image: url('https://images.unsplash.com/photo-1441986300917-64674bd600d8?auto=format&fit=crop&w=1400&q=80')">
            <div class="slide-overlay"></div>
            <div class="slide-content">
              <span class="slide-tag">Pure Monochrome Selection</span>
              <h1>Smart Shopping.<br>Everyday Savings.</h1>
              <p>Experience precision craftsmanship with an automatic 5% store discount and complimentary nationwide doorstep delivery.</p>
              <button class="btn" onclick="document.getElementById('catalog-grid').scrollIntoView({behavior:'smooth'})">Explore All Products</button>
            </div>
            <div class="slide-art">
              <img src="${REAL_PRODUCT_IMAGES['Wireless Headphones']}" alt="Headphones">
            </div>
          </div>

          <!-- Slide 2 -->
          <div class="slide" style="background-image: url('https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=1400&q=80')">
            <div class="slide-overlay"></div>
            <div class="slide-content">
              <span class="slide-tag">Computing &amp; Audio</span>
              <h1>High Performance<br>&amp; Studio Sound.</h1>
              <p>Top-tier laptops, active noise-cancelling headphones, and smartphones engineered for professionals.</p>
              <button class="btn" onclick="selectCategory('Electronics')">View Electronics</button>
            </div>
            <div class="slide-art">
              <img src="${REAL_PRODUCT_IMAGES['Gaming Laptop']}" alt="Laptop">
            </div>
          </div>

          <!-- Slide 3 -->
          <div class="slide" style="background-image: url('https://images.unsplash.com/photo-1490481651871-ab68de25d43d?auto=format&fit=crop&w=1400&q=80')">
            <div class="slide-overlay"></div>
            <div class="slide-content">
              <span class="slide-tag">Minimalist Apparel</span>
              <h1>Contemporary Fits<br>&amp; Daily Essentials.</h1>
              <p>Tailored suits, selvedge denim, heavy fleece hoodies, and premium footwear designed to last.</p>
              <button class="btn" onclick="selectCategory('Clothing')">View Clothing</button>
            </div>
            <div class="slide-art">
              <img src="${REAL_PRODUCT_IMAGES['Running Sneakers']}" alt="Sneakers">
            </div>
          </div>

          <!-- Slide 4 -->
          <div class="slide" style="background-image: url('https://images.unsplash.com/photo-1524758631624-e2822e304c36?auto=format&fit=crop&w=1400&q=80')">
            <div class="slide-overlay"></div>
            <div class="slide-content">
              <span class="slide-tag">Workspace &amp; Lifestyle</span>
              <h1>Ergonomic Design<br>&amp; Fine Details.</h1>
              <p>Precision timepieces, specialty roasted coffees, and ergonomic furniture for elevated living.</p>
              <button class="btn" onclick="selectCategory('Furniture')">View Furniture</button>
            </div>
            <div class="slide-art">
              <img src="${REAL_PRODUCT_IMAGES['Minimal Watch']}" alt="Watch">
            </div>
          </div>
        </div>

        <div class="slider-dots">
          <div class="slider-dot active" onclick="goToSlide(0)"></div>
          <div class="slider-dot" onclick="goToSlide(1)"></div>
          <div class="slider-dot" onclick="goToSlide(2)"></div>
          <div class="slider-dot" onclick="goToSlide(3)"></div>
        </div>
      </div>
    `;
    }

    const countHtml = `<div class="count" id="catalog-grid">Showing ${items.length} of ${products.length} catalog items</div>`;

    let gridHtml = '';
    if (items.length === 0) {
        gridHtml = `<div class="empty"><h3>No products found</h3><p style="margin-top:6px">Try a different search term or category.</p></div>`;
    } else {
        gridHtml = `<div class="grid">` + items.map(p => {
            const rInfo = getProductRatingInfo(p.id);
            return `
      <div class="card" onclick="openProductModal(${p.id})">
        <!-- Full product contain box (entire shoe/shirt visible) -->
        <div class="im">
          <img src="${getProductImage(p)}" alt="${p.name}" loading="lazy" onerror="this.onerror=null;this.src=getProductFallbackSvg('${p.cat}')">
          <span class="bd">-5%</span>
        </div>
        <div class="ci">
          <small>${p.brand} • ${p.cat}</small>
          <h3>${p.name}</h3>
          <div class="rating-pill">
            ${rInfo.count > 0 ? `${rInfo.starsHtml} <span>${rInfo.text}</span>` : `<span style="color:var(--mut);letter-spacing:1px">☆☆☆☆☆</span> <span style="font-size:12px;color:var(--mut)">No reviews yet</span>`}
          </div>
          <div class="pr">
            <b>${formatCurrency(p.final)}</b>
            <s>${formatCurrency(p.price)}</s>
          </div>
          <div class="st ${p.stock <= 10 ? 'low' : ''}">
            ${p.stock > 0 ? (p.stock <= 10 ? `Only ${p.stock} units left in stock` : `${p.stock} available`) : 'Sold Out'}
          </div>
        </div>
      </div>
    `;
        }).join('') + `</div>`;
    }

    main.innerHTML = heroHtml + countHtml + gridHtml;

    if (activeCat === 'All' && !searchQuery) {
        startHeroSlider();
    }
}

// --- Smooth Hero Carousel ---
function startHeroSlider() {
    if (sliderTimer) clearInterval(sliderTimer);
    currentSlide = 0;
    updateSliderPosition();

    const wrap = document.getElementById('hero-slider-wrap');
    if (!wrap) return;

    sliderTimer = setInterval(() => {
        currentSlide = (currentSlide + 1) % 4;
        updateSliderPosition();
    }, 4500);

    wrap.onmouseenter = () => clearInterval(sliderTimer);
    wrap.onmouseleave = () => {
        clearInterval(sliderTimer);
        sliderTimer = setInterval(() => {
            currentSlide = (currentSlide + 1) % 4;
            updateSliderPosition();
        }, 4500);
    };
}

function goToSlide(idx) {
    currentSlide = idx;
    updateSliderPosition();
}

function updateSliderPosition() {
    const track = document.getElementById('hero-slider');
    if (!track) return;
    track.style.transform = `translateX(-${currentSlide * 100}%)`;

    const dots = document.querySelectorAll('.slider-dot');
    dots.forEach((d, i) => d.classList.toggle('active', i === currentSlide));
}

// --- Product Modal (No disappear on color/size, Includes Buy Now, Full Product Contain) ---
async function openProductModal(id) {
    const p = products.find(x => x.id === id);
    if (!p) return;

    currentModalProduct = p;
    modalQty = 1;

    // Filter out invalid or non-main colors (strictly no 'Checkered')
    const validColors = (p.colors && p.colors.length > 0)
        ? p.colors.filter(c => c && c.trim() !== '-' && c.trim().toLowerCase() !== 'checkered')
        : [];
    modalColor = validColors.length > 0 ? validColors[0] : '';

    const validSizes = (p.sizes && p.sizes.length > 0)
        ? p.sizes.filter(s => s && s.trim() !== '-')
        : [];
    modalSize = validSizes.length > 0 ? validSizes[0] : '';

    const modal = document.getElementById('modal');
    if (!modal) return;

    const colorSwatches = (validColors.length > 0) ? `
    <h4>Color: <span id="sel-color" style="font-weight:700;color:var(--fg)">${modalColor || 'Standard'}</span></h4>
    <div class="opts">
      ${validColors.map(c => `
        <button type="button"
                class="sw ${c === modalColor ? 'on' : ''}"
                style="--c:${mapColor(c)}"
                title="${c}"
                onclick="selectColor('${c}', this)"></button>
      `).join('')}
    </div>
  ` : '';

    const sizeChips = (validSizes.length > 0) ? `
    <h4>Size: <span id="sel-size" style="font-weight:700;color:var(--fg)">${modalSize || 'Standard'}</span></h4>
    <div class="opts">
      ${validSizes.map(s => `
        <button type="button"
                class="ch ${s === modalSize ? 'on' : ''}"
                onclick="selectSize('${s}', this)">${s}</button>
      `).join('')}
    </div>
  ` : '';

    const rInfo = getProductRatingInfo(p.id);

    modal.innerHTML = `
    <div class="mb" onclick="if(event.target===this)closeModal()">
      <div class="md">
        <button class="x" onclick="closeModal()">✕</button>
        <div class="pg">
          <div class="pim">
            <img src="${getProductImage(p)}" alt="${p.name}" onerror="this.onerror=null;this.src=getProductFallbackSvg('${p.cat}')">
          </div>
          <div class="pi">
            <small>${p.cat} • ${p.brand}</small>
            <h2>${p.name}</h2>
            <div class="pr big">
              <b>${formatCurrency(p.final)}</b>
              <s>${formatCurrency(p.price)}</s>
            </div>
            <div style="font-size:13px;color:var(--mut);margin-bottom:12px">
              5% store discount applied automatically at checkout.
            </div>
            <hr>
            ${colorSwatches}
            ${sizeChips}
            <h4>Quantity</h4>
            <div class="qty">
              <button type="button" onclick="changeModalQty(-1)">-</button>
              <span id="m-qty">${modalQty}</span>
              <button type="button" onclick="changeModalQty(1)">+</button>
              <em>${p.stock} units available</em>
            </div>

            <!-- Two Clear Options: Buy Now & Add to Cart -->
            <div class="modal-actions">
              <button class="btn btn-buy-now" ${p.stock <= 0 ? 'disabled' : ''} onclick="buyNowFromModal()">
                ${p.stock > 0 ? 'Buy Now' : 'Out of Stock'}
              </button>
              <button class="btn btn-add-cart" ${p.stock <= 0 ? 'disabled' : ''} onclick="addModalToCart()">
                Add to Bag
              </button>
            </div>
          </div>
        </div>

        <!-- Groq AI Product Chat Assistant Section -->
        <div class="ai-chat-section">
          <div class="ai-chat-header">
            <div style="display:flex;align-items:center;gap:8px">
              <span style="display:inline-flex;align-items:center;justify-content:center;width:24px;height:24px;background:var(--inv);color:var(--invt);border-radius:6px;font-size:12px">✦</span>
              <h3 style="font-size:15px;font-weight:800;margin:0">Ask AI About This Product</h3>
            </div>
            <span class="ai-badge">Groq openai/gpt-oss-20b</span>
          </div>
          <p style="color:var(--mut);font-size:12.5px;margin:0 0 10px 0;line-height:1.4">
            Chat with our AI specialist about specifications, sizing, materials, or delivery before buying.
          </p>
          <div class="ai-quick-chips">
            <button type="button" class="ai-chip" onclick="askAiQuickQuestion(${p.id}, 'What are the key specs and materials of this item?')">Specifications?</button>
            <button type="button" class="ai-chip" onclick="askAiQuickQuestion(${p.id}, 'What sizes and fit do you recommend?')">Sizing &amp; Fit?</button>
            <button type="button" class="ai-chip" onclick="askAiQuickQuestion(${p.id}, 'Is delivery free and how does Cash on Delivery work?')">Delivery &amp; COD?</button>
            <button type="button" class="ai-chip" onclick="askAiQuickQuestion(${p.id}, 'What is the return and warranty policy?')">Warranty?</button>
          </div>
          <div class="ai-chat-log" id="ai-chat-log-${p.id}">
            <div class="ai-msg assistant">
              Hello! I'm your SwiftMart shopping assistant for <b>${escapeHtml(p.name)}</b>. Ask me anything about this product before you place your order!
            </div>
          </div>
          <form onsubmit="handleAiChatSubmit(event, ${p.id})" style="display:flex;gap:8px">
            <input type="text" id="ai-chat-input-${p.id}" placeholder="Ask a question about ${escapeHtml(p.name)}..." autocomplete="off" style="flex:1;padding:10px 14px;border-radius:10px;border:1.5px solid var(--bd);background:var(--bg);color:var(--fg);font-size:13px;outline:none">
            <button type="submit" id="ai-chat-send-btn-${p.id}" class="btn" style="padding:10px 18px;font-size:13px;font-weight:700">Ask AI</button>
          </form>
        </div>

        <!-- Customer Reviews Section -->
        <div class="reviews-section">
          <div class="reviews-header">
            <h3 style="font-size:18px;font-weight:800">Customer Reviews</h3>
            <span class="rating-pill" style="font-size:14px" id="modal-reviews-pill">
              ${rInfo.count > 0 ? `${rInfo.starsHtml} ${rInfo.text}` : `<span style="color:var(--mut);letter-spacing:1px">☆☆☆☆☆</span> <span style="color:var(--mut)">No reviews yet</span>`}
            </span>
          </div>
          <div class="reviews-list" id="modal-reviews-list">
            <div style="color:var(--mut);font-size:13px">Loading verified purchaser reviews...</div>
          </div>

          <!-- Verified Review Condition: Only after purchase -->
          ${hasCustomerPurchased(p.id) ? `
            <div class="write-review-card" style="margin-top:20px;padding:18px 20px;background:var(--card);border:1.5px solid var(--bd);border-radius:14px">
              <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
                <h4 style="font-size:14px;font-weight:800;margin:0">Leave a Verified Review</h4>
                <span class="verified-badge-pill">✓ Verified Purchaser</span>
              </div>
              <p style="font-size:12.5px;color:var(--mut);margin-bottom:12px">Thank you for your purchase! Share your rating and feedback on this item.</p>
              <div style="display:flex;gap:12px;margin-bottom:10px;align-items:center;flex-wrap:wrap">
                <input type="text" id="modal-rev-name" value="${escapeHtml(localStorage.getItem('swiftmart_customer_name') || '')}" placeholder="Name used on your order" style="flex:1;min-width:180px;padding:9px 14px;border-radius:8px;border:1.5px solid var(--bd);background:var(--bg);color:var(--fg);font-size:13px">
                <div class="stars-selector" id="modal-stars" style="display:flex;align-items:center;gap:4px;cursor:pointer;font-size:20px;user-select:none">
                  <span onclick="setProductModalStars(1)">★</span>
                  <span onclick="setProductModalStars(2)">★</span>
                  <span onclick="setProductModalStars(3)">★</span>
                  <span onclick="setProductModalStars(4)">★</span>
                  <span onclick="setProductModalStars(5)">★</span>
                </div>
              </div>
              <textarea id="modal-rev-comment" rows="2" placeholder="Write your honest product review..." style="width:100%;padding:10px 14px;border-radius:8px;border:1.5px solid var(--bd);background:var(--bg);color:var(--fg);font-size:13px;resize:none;margin-bottom:10px;box-sizing:border-box"></textarea>
              <button type="button" class="btn" style="width:100%;padding:11px;background:var(--inv);color:var(--invt);border-radius:8px;font-size:13.5px;font-weight:700" onclick="submitProductModalReview(${p.id})">
                Submit Product Review
              </button>
              <div id="modal-rev-feedback" style="font-size:12px;margin-top:8px;text-align:center;font-weight:600"></div>
            </div>
          ` : `
            <div class="verified-lock-box">
              <div style="font-size:22px;margin-bottom:6px">🔒</div>
              <h4 style="font-size:14px;font-weight:800;margin-bottom:6px">Reviews for Verified Buyers Only</h4>
              <p style="font-size:12.5px;color:var(--mut);line-height:1.5;margin-bottom:12px;max-width:440px;margin-left:auto;margin-right:auto">
                To keep our community ratings authentic and reliable, reviews are restricted to customers who have purchased this product. Complete an order for this item to share your verified review.
              </p>
              <div style="display:flex;gap:10px;justify-content:center;align-items:center;flex-wrap:wrap">
                <button type="button" class="btn" style="font-size:12px;padding:7px 16px" onclick="buyNowFromModal()">Buy Now to Review</button>
                <button type="button" class="btn alt" style="font-size:12px;padding:7px 16px" onclick="toggleOrderVerifySection(${p.id})">Already Ordered? Verify Order</button>
              </div>
              <div id="verify-order-section-${p.id}" style="display:none;margin-top:14px;padding-top:14px;border-top:1px dashed var(--bd)">
                <div style="display:flex;gap:8px;max-width:360px;margin:auto">
                  <input type="text" id="verify-order-id-${p.id}" placeholder="Enter Order ID (e.g. ORD-12345678)" style="flex:1;padding:8px 12px;font-size:12.5px;border-radius:8px;border:1px solid var(--bd);background:var(--bg);color:var(--fg)">
                  <button type="button" class="btn" style="padding:8px 14px;font-size:12.5px" onclick="verifyOrderToUnlockReview(${p.id})">Verify</button>
                </div>
                <div id="verify-order-fb-${p.id}" style="font-size:12px;margin-top:8px"></div>
              </div>
            </div>
          `}
        </div>
      </div>
    </div>
  `;

    loadProductReviews(p.id);
    modalReviewStars = 5;
    updateProductModalStarsDisplay();
}

// Fixed: Toggles DOM attributes in place. Window never disappears!
function selectColor(colorName, el) {
    modalColor = colorName;
    const label = document.getElementById('sel-color');
    if (label) label.textContent = colorName;
    document.querySelectorAll('#modal .sw').forEach(b => b.classList.remove('on'));
    if (el) el.classList.add('on');
}

function selectSize(sizeName, el) {
    modalSize = sizeName;
    const label = document.getElementById('sel-size');
    if (label) label.textContent = sizeName;
    document.querySelectorAll('#modal .ch').forEach(b => b.classList.remove('on'));
    if (el) el.classList.add('on');
}

function changeModalQty(d) {
    if (!currentModalProduct) return;
    const max = currentModalProduct.stock;
    modalQty = Math.max(1, Math.min(max, modalQty + d));
    const el = document.getElementById('m-qty');
    if (el) el.textContent = modalQty;
}

function closeModal() {
    currentModalProduct = null;
    const modal = document.getElementById('modal');
    if (modal) modal.innerHTML = '';
}

function mapColor(c) {
    const map = {
        'Black': '#000000',
        'White': '#f5f5f5',
        'Grey': '#525252',
        'Charcoal': '#27272a',
        'Silver': '#d4d4d4',
        'Blue': '#1e293b',
        'Navy': '#0f172a',
        'Red': '#7f1d1d',
        'Brown': '#451a03',
        'Beige': '#d4b996',
        'Khaki': '#a39276',
        'Olive': '#3f422e',
        'Gold': '#78350f',
        'Orange': '#c2410c',
        'Yellow': '#ca8a04'
    };
    return map[c] || '#000000';
}

let modalReviewStars = 5;

function setProductModalStars(n) {
    modalReviewStars = n;
    updateProductModalStarsDisplay();
}

function updateProductModalStarsDisplay() {
    const container = document.getElementById('modal-stars');
    if (!container) return;
    const spans = container.querySelectorAll('span');
    spans.forEach((s, idx) => {
        s.className = (idx < modalReviewStars) ? 'star-on' : 'star-off';
    });
}

async function submitProductModalReview(productId) {
    if (!hasCustomerPurchased(productId)) {
        const fb = document.getElementById('modal-rev-feedback');
        if (fb) {
            fb.style.color = 'var(--err)';
            fb.textContent = 'Only verified buyers who have purchased this item can leave a review.';
        }
        return;
    }
    const nameEl = document.getElementById('modal-rev-name');
    const commentEl = document.getElementById('modal-rev-comment');
    const fb = document.getElementById('modal-rev-feedback');
    const name = nameEl ? nameEl.value.trim() : '';
    const comment = commentEl ? commentEl.value.trim() : '';
    const orderId = getReviewOrderId(productId);

    if (!name.match(/^[a-zA-Z\s]{2,40}$/)) {
        if (fb) { fb.style.color = 'var(--err)'; fb.textContent = 'Please enter a valid name (alphabets only, 2-40 chars).'; }
        return;
    }
    if (comment.length < 3) {
        if (fb) { fb.style.color = 'var(--err)'; fb.textContent = 'Please write at least 3 characters for your review.'; }
        return;
    }

    try {
        const params = new URLSearchParams({
            id: String(productId),
            name: name,
            stars: String(modalReviewStars),
            comment: comment,
            orderId: orderId
        });
        const res = await fetch('/api/reviews', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params.toString()
        });
        if (res.ok) {
            if (fb) { fb.style.color = 'var(--fg)'; fb.textContent = '✓ Thank you! Your review has been published.'; }
            if (commentEl) commentEl.value = '';
            await loadProductReviews(productId);
            await loadCatalog(); // Refresh review count & rating pill on catalog card
            showToast('Review posted successfully!');
        } else {
            let msg = 'Failed to post review. Please try again.';
            try { const d = await res.json(); if (d && d.error) msg = d.error; } catch (_) {}
            if (fb) { fb.style.color = 'var(--err)'; fb.textContent = msg; }
        }
    } catch (err) {
        if (fb) { fb.style.color = 'var(--err)'; fb.textContent = err.message; }
    }
}

// --- Fetch & Render Reviews for Product ---

// --- Verified Purchaser Tracking System ---
// UX only. The server re-checks Order ID + customer name + product on every review.
const REVIEW_ORDERS_KEY = 'swiftmart_review_orders';

function getReviewOrders() {
    try { return JSON.parse(localStorage.getItem(REVIEW_ORDERS_KEY) || '{}') || {}; }
    catch (e) { return {}; }
}

function getReviewOrderId(productId) {
    return getReviewOrders()[String(Number(productId))] || '';
}

function recordCustomerPurchase(productIds, customerName, orderId) {
    try {
        if (orderId) {
            const map = getReviewOrders();
            productIds.forEach(id => { map[String(Number(id))] = orderId; });
            localStorage.setItem(REVIEW_ORDERS_KEY, JSON.stringify(map));
        }
        if (customerName) {
            localStorage.setItem('swiftmart_customer_name', customerName);
        }
    } catch (e) {
        console.warn('Failed to record customer purchase:', e);
    }
}

function hasCustomerPurchased(productId) {
    return !!getReviewOrderId(productId);
}

async function verifyOrderToUnlockReview(productId) {
    const input = document.getElementById(`verify-order-id-${productId}`);
    const fb = document.getElementById(`verify-order-fb-${productId}`);
    const orderId = input ? input.value.trim().toUpperCase() : '';
    if (!orderId) {
        if (fb) { fb.style.color = 'var(--err)'; fb.textContent = 'Please enter your Order ID.'; }
        return;
    }
    if (fb) { fb.style.color = 'var(--mut)'; fb.textContent = 'Verifying order records...'; }
    try {
        const res = await fetch(`/api/receipt?id=${encodeURIComponent(orderId)}`);
        if (!res.ok) {
            if (fb) { fb.style.color = 'var(--err)'; fb.textContent = `Order "${orderId}" not found. Please check your receipt.`; }
            return;
        }
        const text = await res.text();
        const p = products.find(x => x.id === productId);
        if (p && text.includes(p.name)) {
            recordCustomerPurchase([productId], null, orderId);
            if (fb) { fb.style.color = '#16a34a'; fb.textContent = '✓ Verified! Order confirmed.'; }
            showToast('Order verified! You can now write a review.');
            setTimeout(() => {
                if (currentModalProduct && currentModalProduct.id === productId) {
                    renderProductModal(currentModalProduct);
                }
            }, 600);
        } else {
            if (fb) { fb.style.color = 'var(--err)'; fb.textContent = `Order "${orderId}" does not contain this item.`; }
        }
    } catch (err) {
        if (fb) { fb.style.color = 'var(--err)'; fb.textContent = 'Verification error: ' + err.message; }
    }
}

function toggleOrderVerifySection(productId) {
    const el = document.getElementById(`verify-order-section-${productId}`);
    if (el) {
        const isHidden = el.style.display === 'none' || !el.style.display;
        el.style.display = isHidden ? 'block' : 'none';
        if (isHidden) {
            setTimeout(() => document.getElementById(`verify-order-id-${productId}`)?.focus(), 60);
        }
    }
}

// --- Groq AI Product Chat Assistant Engine ---
let aiChatSessions = {}; // Map of productId -> array of messages [{ role, content }]

function askAiQuickQuestion(productId, question) {
    const input = document.getElementById(`ai-chat-input-${productId}`);
    if (input) input.value = question;
    submitAiProductChat(productId, question);
}

function handleAiChatSubmit(e, productId) {
    e.preventDefault();
    const input = document.getElementById(`ai-chat-input-${productId}`);
    const q = input ? input.value.trim() : '';
    if (!q) return;
    submitAiProductChat(productId, q);
    if (input) input.value = '';
}

async function submitAiProductChat(productId, question) {
    const log = document.getElementById(`ai-chat-log-${productId}`);
    const sendBtn = document.getElementById(`ai-chat-send-btn-${productId}`);
    const input = document.getElementById(`ai-chat-input-${productId}`);
    const p = products.find(x => x.id === productId);
    if (!p || !log) return;

    // Append user query bubble
    const userBubble = document.createElement('div');
    userBubble.className = 'ai-msg user';
    userBubble.textContent = question;
    log.appendChild(userBubble);
    log.scrollTop = log.scrollHeight;

    // Append loading placeholder
    const loadingBubble = document.createElement('div');
    loadingBubble.className = 'ai-msg loading';
    loadingBubble.innerHTML = '✦ Consulting Groq AI product advisor...';
    log.appendChild(loadingBubble);
    log.scrollTop = log.scrollHeight;

    if (sendBtn) sendBtn.disabled = true;
    if (input) input.disabled = true;

    if (!aiChatSessions[productId]) {
        aiChatSessions[productId] = [];
    }
    aiChatSessions[productId].push({ role: 'user', content: question });

    try {
        const reply = await queryGroqAiForProduct(p, aiChatSessions[productId]);
        loadingBubble.className = 'ai-msg assistant';
        loadingBubble.innerHTML = formatAiResponseHtml(reply);
        aiChatSessions[productId].push({ role: 'assistant', content: reply });
    } catch (err) {
        console.warn('Groq AI API note:', err.message);
        const fallbackReply = generateSmartProductReply(p, question);
        loadingBubble.className = 'ai-msg assistant';
        loadingBubble.innerHTML = formatAiResponseHtml(fallbackReply);
        aiChatSessions[productId].push({ role: 'assistant', content: fallbackReply });
    } finally {
        if (sendBtn) sendBtn.disabled = false;
        if (input) {
            input.disabled = false;
            input.focus();
        }
        log.scrollTop = log.scrollHeight;
    }
}

async function queryGroqAiForProduct(product, chatHistory) {
    const apiKey = (GROQ_API_KEY || localStorage.getItem('swiftmart_groq_key') || '').trim();
    if (!apiKey) {
        return generateSmartProductReply(product, chatHistory[chatHistory.length - 1].content);
    }

    const systemPrompt = `You are an expert product specialist and shopping advisor for SwiftMart, a luxury monochrome e-commerce store.
You are chatting with a shopper who is considering buying this exact item right now:
- Product Name: ${product.name}
- Brand: ${product.brand}
- Category: ${product.cat}
- Original Retail Price: $${product.price.toFixed(2)} (PKR Rs. ${Math.round(product.price * 278.5).toLocaleString()})
- Final Price with 5% Instant Store Discount: $${product.final.toFixed(2)} (PKR Rs. ${Math.round(product.final * 278.5).toLocaleString()})
- Current Stock Available: ${product.stock} units
- Available Sizes: ${product.sizes && product.sizes.length && product.sizes[0] !== '-' ? product.sizes.join(', ') : 'Standard / One-Size'}
- Available Colors: ${product.colors && product.colors.length && product.colors[0] !== '-' ? product.colors.join(', ') : 'Standard'}
- Delivery Policy: Free nationwide delivery across Pakistan via Cash on Delivery (pay rider upon arrival).
- Return Policy: 7-day hassle-free replacement. 100% genuine guaranteed.

Guidelines:
1. Provide a direct, polite, helpful answer in 2-4 sentences.
2. Address specific questions regarding sizing, fit, materials, durability, compatibility, delivery, or pricing.
3. Be friendly and encourage confidence in their purchase decision.`;

    const messages = [
        { role: 'system', content: systemPrompt },
        ...chatHistory.slice(-6)
    ];

    let res;
    const chatPayload = JSON.stringify({
        model: GROQ_MODEL,
        messages: messages,
        temperature: 0.35,
        max_completion_tokens: 1024
    });
    try {
        res = await fetch(GROQ_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${apiKey}`
            },
            body: chatPayload
        });
    } catch (netErr) {
        res = await fetch('/api/groq', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${apiKey}`
            },
            body: chatPayload
        });
    }

    if (!res.ok) {
        throw new Error(`Groq API returned HTTP ${res.status}`);
    }

    const data = await res.json();
    const msg = data.choices?.[0]?.message;
    let reply = (msg?.content || '').trim();
    if (!reply && msg?.reasoning) {
        reply = msg.reasoning.trim();
    }
    if (!reply) {
        throw new Error('Empty response from Groq API');
    }
    return reply;
}

function generateSmartProductReply(p, question) {
    const q = (question || '').toLowerCase();
    const curPkr = Math.round(p.final * 278.5).toLocaleString();
    const sizes = (p.sizes && p.sizes.length && p.sizes[0] !== '-') ? p.sizes.join(', ') : 'Standard one-size';
    const colors = (p.colors && p.colors.length && p.colors[0] !== '-') ? p.colors.join(', ') : 'Classic finish';

    if (q.includes('size') || q.includes('fit') || q.includes('measurement')) {
        return `The **${p.name}** is currently available in sizes: **${sizes}**. Sizing runs true to standard retail fit, and you can select your regular size.`;
    }
    if (q.includes('color') || q.includes('colour') || q.includes('shade')) {
        return `The **${p.name}** comes in: **${colors}**, featuring our signature luxury finish.`;
    }
    if (q.includes('price') || q.includes('cost') || q.includes('discount') || q.includes('deal') || q.includes('pkr') || q.includes('rupee')) {
        return `The **${p.name}** is priced at **${formatCurrency(p.final)}** (PKR Rs. ${curPkr}), which already includes an automatic **5% instant store discount** off the regular ${formatCurrency(p.price)} price!`;
    }
    if (q.includes('stock') || q.includes('available') || q.includes('left') || q.includes('inventory')) {
        return p.stock > 0
            ? `Good news! We currently have **${p.stock} units** of the **${p.name}** in stock and ready to dispatch immediately.`
            : `The **${p.name}** is currently out of stock. Please check back shortly for our next restock.`;
    }
    if (q.includes('deliver') || q.includes('ship') || q.includes('cod') || q.includes('cash on delivery') || q.includes('time')) {
        return `We offer **Free Nationwide Delivery** across Pakistan! You can pay safely via **Cash on Delivery (COD)** when the courier rider delivers the parcel to your doorstep.`;
    }
    if (q.includes('return') || q.includes('warranty') || q.includes('guarantee') || q.includes('refund')) {
        return `SwiftMart provides a **7-Day Hassle-Free Return & Replacement Guarantee** on the **${p.name}**. All products are 100% authentic and quality-inspected before shipment.`;
    }
    if (q.includes('spec') || q.includes('feature') || q.includes('material') || q.includes('quality')) {
        return `The **${p.name}** by **${p.brand}** in our **${p.cat}** collection is crafted with premium materials designed for daily durability and timeless style. It is verified authentic and backed by our satisfaction guarantee.`;
    }
    return `The **${p.name}** is one of our top-rated items in the **${p.cat}** lineup by **${p.brand}**. It is in stock (${p.stock} units) at **${formatCurrency(p.final)}** with free nationwide Cash on Delivery. Let me know if you have any questions before purchasing!`;
}

function formatAiResponseHtml(text) {
    if (!text) return '';
    let escaped = escapeHtml(text);
    escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<b>$1</b>');
    escaped = escaped.replace(/^\s*[-•]\s+(.*)$/gm, '&bull; $1<br>');
    return escaped.replace(/\n/g, '<br>');
}

async function loadProductReviews(productId) {
    const container = document.getElementById('modal-reviews-list');
    if (!container) return;
    try {
        const res = await fetch(`/api/reviews?id=${productId}`);
        const list = await res.json();
        reviewsByProduct[productId] = list || [];

        const pill = document.getElementById('modal-reviews-pill');
        if (pill) {
            const rInfo = getProductRatingInfo(productId);
            pill.innerHTML = (rInfo.count > 0)
                ? `${rInfo.starsHtml} ${rInfo.text}`
                : `<span style="color:var(--mut);letter-spacing:1px">☆☆☆☆☆</span> <span style="color:var(--mut)">No reviews yet</span>`;
        }

        if (!list || list.length === 0) {
            container.innerHTML = `<div style="color:var(--mut);font-size:13px;padding:8px 0">No reviews written yet. Be the first to review after purchase!</div>`;
            return;
        }
        container.innerHTML = list.map(r => `
      <div class="review-card">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px">
          <strong>${escapeHtml(r.name)}</strong>
          <span style="color:var(--mut);font-size:11.5px">${r.date}</span>
        </div>
        <div class="review-stars" style="font-size:12px;margin-bottom:6px">${'★'.repeat(r.stars)}${'☆'.repeat(5 - r.stars)}</div>
        <p style="color:var(--fg);font-size:13px">${escapeHtml(r.comment)}</p>
      </div>
    `).join('');
    } catch (e) {
        container.innerHTML = `<div style="color:var(--mut);font-size:13px">Could not load reviews.</div>`;
    }
}

// --- "Buy Now" Option: Direct 1-Click to Checkout ---
function buyNowFromModal() {
    if (!currentModalProduct) return;
    const product = currentModalProduct;
    const qty = modalQty;
    const size = modalSize || '-';
    const color = modalColor || '-';

    addToCart(product, qty, size, color);
    closeModal();

    // Instantly open drawer and focus checkout form
    dr(true);
    setTimeout(() => {
        const nameInput = document.getElementById('f-name');
        if (nameInput) nameInput.focus();
    }, 200);
}

// --- Sleek Add-To-Cart Notification Modal ---
function addModalToCart() {
    if (!currentModalProduct) return;
    const product = currentModalProduct;
    const qty = modalQty;
    const size = modalSize || '-';
    const color = modalColor || '-';

    addToCart(product, qty, size, color);
    closeModal();
    showAddedModal(product, qty, size, color);
}

function showAddedModal(p, qty, size, color) {
    closeAddedModal();
    const c = document.getElementById('added-modal-container');
    if (!c) return;

    const modal = document.createElement('div');
    modal.id = 'added-modal-instance';
    modal.className = 'mb';
    modal.onclick = (e) => { if (e.target === modal) closeAddedModal(); };

    modal.innerHTML = `
    <div class="added-modal">
      <button class="x" onclick="closeAddedModal()">✕</button>
      <div class="added-h">
        <div class="added-check">✓</div>
        <div>
          <h3 style="font-size:17px;font-weight:800">Added to Shopping Bag</h3>
          <small style="color:var(--mut)">Item placed in your order summary</small>
        </div>
      </div>
      <div class="added-item">
        <img src="${getProductImage(p)}" alt="${p.name}">
        <div style="flex:1">
          <strong style="font-size:14.5px;display:block">${p.name}</strong>
          <small style="color:var(--mut);display:block;margin-top:2px">
            Qty: ${qty} ${size !== '-' ? '• Size: ' + size : ''} ${color !== '-' ? '• Color: ' + color : ''}
          </small>
          <div style="font-weight:800;margin-top:4px;font-size:15px">
            ${formatCurrency(p.final * qty)}
          </div>
        </div>
      </div>
      <div style="display:flex;gap:10px">
        <button class="btn alt" style="flex:1" onclick="closeAddedModal()">Continue Shopping</button>
        <button class="btn" style="flex:1" onclick="closeAddedModal(); dr(true)">View Bag &amp; Checkout</button>
      </div>
    </div>
  `;
    c.appendChild(modal);
}

function closeAddedModal() {
    const el = document.getElementById('added-modal-instance');
    if (el) el.remove();
}

// --- Cart State Management ---
function addToCart(product, qty, size, color) {
    const key = `${product.id}|${size}|${color}`;
    const existing = cart.find(item => item.key === key);

    if (existing) {
        existing.qty = Math.min(product.stock, existing.qty + qty);
    } else {
        cart.push({ key, id: product.id, name: product.name, price: product.price, final: product.final, size, color, qty });
    }

    saveCart();
    updateCartBadge();
}

function saveCart() {
    localStorage.setItem('swiftmart_cart', JSON.stringify(cart));
}

function updateCartBadge() {
    const cnt = document.getElementById('cnt');
    if (cnt) {
        const total = cart.reduce((sum, item) => sum + item.qty, 0);
        cnt.textContent = total;
    }
}

function dr(open) {
    const drawer = document.getElementById('drawer');
    const ov = document.getElementById('ov');
    if (open) {
        renderCartDrawer();
        drawer?.classList.add('open');
        ov?.classList.add('on');
    } else {
        drawer?.classList.remove('open');
        ov?.classList.remove('on');
    }
}

// --- Clean Shopping Cart Drawer (Label changed strictly to "Total:") ---
function renderCartDrawer() {
    const drawer = document.getElementById('drawer');
    if (!drawer) return;

    const total = cart.reduce((sum, item) => sum + (item.final * item.qty), 0);

    let itemsHtml = '';
    if (cart.length === 0) {
        itemsHtml = `<div class="empty" style="padding:48px 10px">Your shopping bag is empty.</div>`;
    } else {
        itemsHtml = cart.map(item => {
            const p = products.find(x => x.id === item.id) || { name: item.name, final: item.final, cat: 'Item', brand: '' };
            return `
        <div class="li">
          <div class="li-im">
            <img src="${getProductImage(p)}" alt="${item.name}">
          </div>
          <div>
            <strong>${item.name}</strong>
            <small>${item.size !== '-' ? 'Size: ' + item.size : ''} ${item.color !== '-' ? ' | ' + item.color : ''}</small>
            <div class="qty sm" style="margin-top:6px">
              <button onclick="updateCartQty('${item.key}', -1)">-</button>
              <span>${item.qty}</span>
              <button onclick="updateCartQty('${item.key}', 1)">+</button>
            </div>
          </div>
          <div class="lr">
            <b>${formatCurrency(item.final * item.qty)}</b>
            <a onclick="removeCartItem('${item.key}')">Remove</a>
          </div>
        </div>
      `;
        }).join('');
    }

    drawer.innerHTML = `
    <div class="dh">
      <h2>Shopping Bag</h2>
      <button class="x" onclick="dr(false)">✕</button>
    </div>
    <div class="dl">
      ${itemsHtml}
    </div>
    ${cart.length > 0 ? `
      <div class="df">
        <!-- Changed strictly to "Total" as requested -->
        <div class="tt">
          <span>Total:</span>
          <b>${formatCurrency(total)}</b>
        </div>
        <form class="fm" id="checkout-form" onsubmit="submitCheckout(event)">
          <label>Full Name
            <input name="name" id="f-name" required placeholder="e.g. Muhammad Ali" autocomplete="name" oninput="validateNameInput(this)">
          </label>
          <div class="err" id="err-name"></div>

          <label>Phone Number
            <input name="phone" id="f-phone" required placeholder="e.g. +92 300 1234567" autocomplete="tel" oninput="validatePhoneInput(this)">
          </label>
          <div class="err" id="err-phone"></div>

          <label>Email Address
            <input name="email" id="f-email" type="email" required placeholder="e.g. ali@example.com" autocomplete="email" oninput="validateEmailInput(this)">
          </label>
          <div class="err" id="err-email"></div>

                         <label>Country
            <input name="country" id="f-country" value="Pakistan" readonly tabindex="-1" style="cursor:not-allowed;opacity:0.75">
          </label>
          <label>City
            <input name="city" id="f-city" list="dl-city" required autocomplete="off" placeholder="Type or select city" oninput="onCityChange()">
            <datalist id="dl-city">${dlOptions(Object.keys(ADDRESS_DATA['Pakistan']))}</datalist>
          </label>
          <label>Area / Sector
            <input name="area" id="f-area" list="dl-area" required disabled autocomplete="off" placeholder="Select city first" oninput="onAreaChange()">
            <datalist id="dl-area"></datalist>
          </label>
          <label>House No / Street
            <input name="house" id="f-house" required placeholder="e.g. House 14 Street 5" autocomplete="address-line1" oninput="validateAddressFields()">
          </label>
          <div class="err" id="err-address"></div>

          <label>Landmark / Delivery Notes (optional)
            <input name="note" id="f-note" maxlength="120" autocomplete="off" placeholder="e.g. Near Dolmen Mall, blue gate">
          </label>
          <div class="err" id="err-note"></div>

          <div>
            <div style="font-size:12px;font-weight:700;color:var(--mut);text-transform:uppercase;letter-spacing:0.04em">Exact Delivery Location (map pin)</div>
            <div style="display:flex;gap:8px;margin-top:6px">
              <button type="button" class="btn alt" style="flex:1;padding:10px 12px;font-size:12.5px" onclick="toggleDeliveryMap()">Pin on map</button>
              <button type="button" class="btn alt" style="flex:1;padding:10px 12px;font-size:12.5px" onclick="useMyLocation()">Use my location</button>
            </div>
            <div id="pin-status" style="font-size:12px;color:var(--mut);margin-top:6px;line-height:1.4">${pinStatusHtml()}</div>
            <div class="err" id="err-pin"></div>
          </div>

          <div style="border:1.5px solid var(--bd);border-radius:12px;padding:12px 14px;background:var(--card)">
            <div style="font-size:12px;font-weight:700;color:var(--mut);text-transform:uppercase;letter-spacing:0.04em">Payment Method</div>
            <div style="margin-top:6px;font-size:13.5px;font-weight:700">&#128181; Cash on Delivery</div>
            <div style="font-size:12px;color:var(--mut);margin-top:2px;line-height:1.4">Pay in cash to the rider when your order arrives. No online payment is needed.</div>
          </div>

          <div class="err" id="err-cart"></div>
          <button class="btn" type="submit" id="chk-btn" style="margin-top:8px">Place Order (Cash on Delivery)</button>
        </form>
      </div>
    ` : ''}
  `;
}

// --- Real-Time Validations ---
function validateNameInput(el) {
    const val = el.value.trim();
    const err = document.getElementById('err-name');
    if (!val) { el.classList.remove('bad'); if (err) err.textContent = ''; return true; }
    if (!/^[a-zA-Z\s]+$/.test(val)) {
        el.classList.add('bad');
        if (err) err.textContent = 'Name must contain only alphabets and spaces (no numbers or symbols).';
        return false;
    } else if (val.length < 3) {
        el.classList.add('bad');
        if (err) err.textContent = 'Name must be at least 3 letters long.';
        return false;
    } else {
        el.classList.remove('bad');
        if (err) err.textContent = '';
        return true;
    }
}

function validatePhoneInput(el) {
    const val = el.value.trim();
    const err = document.getElementById('err-phone');
    if (!val) { el.classList.remove('bad'); if (err) err.textContent = ''; return true; }
    const clean = val.replace(/[\s\-]/g, '');
    const cEl = document.getElementById('f-country');
    const country = cEl ? addrMatch(Object.keys(ADDRESS_DATA), cEl.value) : '';
    const ok = country ? PHONE_RULES[country].re.test(clean) : Object.values(PHONE_RULES).some(r => r.re.test(clean));
    if (!ok) {
        el.classList.add('bad');
        if (err) err.textContent = country
            ? `Enter a valid ${country} mobile number (e.g. ${PHONE_RULES[country].hint}).`
            : 'Enter a valid mobile number with country code (e.g. +92 300 1234567).';
        return false;
    } else {
        el.classList.remove('bad');
        if (err) err.textContent = '';
        return true;
    }
}

function validateEmailInput(el) {
    const val = el.value.trim();
    const err = document.getElementById('err-email');
    if (!val) { el.classList.remove('bad'); if (err) err.textContent = ''; return true; }
    if (!/^[a-zA-Z0-9._+-]+@gmail\.com$/i.test(val)) {
        el.classList.add('bad');
        if (err) err.textContent = 'Email must be a valid @gmail.com address.';    return false;
    } else {
        el.classList.remove('bad');
        if (err) err.textContent = '';
        return true;
    }
}

function onCityChange() {
    const g = id => document.getElementById(id);
    const country = addrMatch(Object.keys(ADDRESS_DATA), g('f-country').value);
    const cities = ADDRESS_DATA[country] || {};
    const cityEl = g('f-city'), areaEl = g('f-area');
    const city = addrMatch(Object.keys(cities), cityEl.value);
    if (cityEl.dataset.sel !== city) {
        cityEl.dataset.sel = city;
        areaEl.value = '';
    }
    g('dl-area').innerHTML = dlOptions(city ? cities[city] : []);
    areaEl.disabled = !city;
    areaEl.placeholder = city ? 'Type or select area' : 'Select city first';
    recenterMapToCity();
    validateDeliveryPin(false);
    validateAddressFields();
}

function onAreaChange() {
    recenterMapToCity();
    validateDeliveryPin(false);
    validateAddressFields();
}

function validateAddressFields(strict) {
    const g = id => document.getElementById(id);
    const err = g('err-address');
    const country = addrMatch(Object.keys(ADDRESS_DATA), g('f-country').value);
    const cities = ADDRESS_DATA[country] || {};
    const city = addrMatch(Object.keys(cities), g('f-city').value);
    const area = addrMatch(cities[city] || [], g('f-area').value);
    const house = g('f-house').value.trim();
    const houseOk = house.length >= 3 && /\d/.test(house) && /^[A-Za-z0-9\s#.\/-]+$/.test(house);
    const checks = [
        ['f-country', !!country, 'Please select a country from the list.', !g('f-country').value.trim()],
        ['f-city', !!city, 'Please select a city from the list.', !g('f-city').value.trim()],
        ['f-area', !!area, 'Please select an area from the list.', !g('f-area').value.trim()],
        ['f-house', houseOk, 'House No must include a number, letters/numbers only (e.g. House 14 Street 5).', !house]
    ];
    let msg = '';
    for (const [id, valid, text, empty] of checks) {
        const showBad = strict ? !valid : (!valid && !empty);
        g(id).classList.toggle('bad', showBad);
        if (showBad && !msg) msg = text;
    }
    if (err) err.textContent = msg;
    return checks.every(c => c[1]);
}

function updateCartQty(key, delta) {
    const item = cart.find(x => x.key === key);
    if (!item) return;

    const product = products.find(x => x.id === item.id);
    const max = product ? product.stock : 999;

    item.qty += delta;
    if (item.qty <= 0) {
        cart = cart.filter(x => x.key !== key);
    } else if (item.qty > max) {
        item.qty = max;
        toast(`Maximum stock reached for ${item.name}`);
    }

    saveCart();
    updateCartBadge();
    renderCartDrawer();
}

function removeCartItem(key) {
    cart = cart.filter(x => x.key !== key);
    saveCart();
    updateCartBadge();
    renderCartDrawer();
}

// --- Order Submission, Mind-Blowing Confirmation & Optional Receipt ---
async function submitCheckout(e) {
    e.preventDefault();
    const form = e.target;

    const nameEl = form.name;
    const phoneEl = form.phone;
    const emailEl = form.email;

    if (!validateNameInput(nameEl) || !validatePhoneInput(phoneEl) || !validateEmailInput(emailEl) || !validateAddressFields(true) || !validateDeliveryPin(true)) return;

    const name = nameEl.value.trim();
    const phone = phoneEl.value.trim();
    const email = emailEl.value.trim();
    const cCountry = addrMatch(Object.keys(ADDRESS_DATA), form.country.value);
    const cCity = addrMatch(Object.keys(ADDRESS_DATA[cCountry] || {}), form.city.value);
    const cArea = addrMatch((ADDRESS_DATA[cCountry] || {})[cCity] || [], form.area.value);
    const address = [form.house.value.trim(), cArea, cCity, cCountry].join(', ');
    const note = form.note.value.trim();
    const purchasedItems = [...cart];
    const itemsParam = cart.map(it => `${it.id}:${it.qty}:${it.size || '-'}:${it.color || '-'}`).join(';');

    const btn = document.getElementById('chk-btn');
    if (btn) { btn.disabled = true; btn.textContent = 'Processing Order...'; }

    try {
        const params = new URLSearchParams({ name, phone, email, address, note, lat: String(deliveryPin.lat), lng: String(deliveryPin.lng), items: itemsParam });
        const res = await fetch('/api/checkout', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params.toString()
        });

        const data = await res.json();
        if (!res.ok) {
            if (data.field) {
                showFieldError(data.field, data.error);
            } else {
                showFieldError('cart', data.error || 'Checkout failed.');
            }
            return;
        }

        // Success: Empty cart, record verified purchaser items & open Order Confirmation Window
        const purchasedIds = purchasedItems.map(it => Number(it.id));
        recordCustomerPurchase(purchasedIds, name, data.id);
        cart = [];
        deliveryPin = null;
        pinPlace = null;
        lastLookupKey = '';
        if (deliveryMap) { deliveryMap.remove(); deliveryMap = null; deliveryMarker = null; areaZoneCircle = null; }
        saveCart();
        updateCartBadge();
        renderCartDrawer(); // Reset drawer to clean empty state
        dr(false);          // Close sidebar drawer
        await loadCatalog(); // Sync real stock

        openOrderConfirmationModal(data, name, purchasedItems);
    } catch (err) {
        showFieldError('cart', 'Server communication error: ' + err.message);
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = 'Place Order (Cash on Delivery)'; }
    }
}

function showFieldError(field, msg) {
    const errEl = document.getElementById('err-' + field);
    const inputEl = document.getElementById('f-' + field);
    if (errEl) errEl.textContent = msg;
    if (inputEl) inputEl.classList.add('bad');
}

// Centered Modal Order Confirmation Window
function openOrderConfirmationModal(orderData, customerName, items) {
    const modal = document.getElementById('modal');
    if (!modal) return;

    const primaryItem = (items && items.length > 0) ? items[0] : { id: 101, name: 'SwiftMart Purchase' };
    const itemsSummary = (items && items.length > 0)
        ? items.map(it => `${it.qty}x ${escapeHtml(it.name)}${it.size && it.size !== '-' ? ` (${it.size})` : ''}${it.color && it.color !== '-' ? ` [${it.color}]` : ''}`).join(', ')
        : 'Purchased items';

    modal.innerHTML = `
    <div class="mb" onclick="if(event.target===this)closeModal()">
      <div class="md" style="max-width:540px;padding:36px 30px;text-align:center">
        <button class="x" onclick="closeModal()">✕</button>

        <div class="ok" style="padding:0">
          <i>✓</i>
          <h2 style="font-size:24px;font-weight:900;letter-spacing:-0.02em;margin-bottom:6px">Order Confirmed!</h2>
          <p style="color:var(--mut);font-size:14px;margin-bottom:20px;line-height:1.5">
            Thank you, <b>${escapeHtml(customerName)}</b>. Your order has been placed successfully.
          </p>
        </div>

        <!-- Order Summary Card -->
        <div style="background:var(--card);border:1.5px solid var(--bd);border-radius:16px;padding:16px 20px;margin-bottom:16px;text-align:left">
          <div style="display:flex;justify-content:space-between;margin-bottom:8px;font-size:13.5px">
            <span style="color:var(--mut)">Order Reference:</span>
            <strong style="font-family:monospace;letter-spacing:0.04em">${escapeHtml(orderData.id)}</strong>
          </div>
          <div style="display:flex;justify-content:space-between;margin-bottom:10px;font-size:13.5px">
            <span style="color:var(--mut)">Pay on Delivery:</span>
            <strong style="font-size:15px;color:var(--fg)">${formatCurrency(Number(orderData.total))}</strong>
          </div>
          <div style="display:flex;justify-content:space-between;gap:12px;font-size:12.5px;border-top:1px dashed var(--bd);padding-top:10px;color:var(--mut)">
            <span>Items:</span>
            <span style="color:var(--fg);font-weight:600;text-align:right">${itemsSummary}</span>
          </div>
        </div>

        <!-- Official Receipt Section (Optional) -->
        <div style="background:var(--card);border:1.5px solid var(--bd);border-radius:16px;padding:16px 20px;margin-bottom:16px;text-align:left">
          <strong style="font-size:13.5px;display:block;margin-bottom:4px">Official Order Receipt</strong>
          <p style="font-size:12.5px;color:var(--mut);margin-bottom:12px;line-height:1.4">
            Stored in server audit records. Downloading is optional for your personal file handling.
          </p>
          <a href="/api/receipt?id=${orderData.id}" download="Receipt-${orderData.id}.txt" class="btn" style="width:100%;font-size:13px;background:var(--bg);color:var(--fg);border:1.5px solid var(--bd);border-radius:10px;padding:10px;display:inline-flex;justify-content:center;align-items:center;text-decoration:none;font-weight:700">
            📥 Download Receipt (.txt)
          </a>
        </div>

        <!-- Continue Shopping Button -->
        <button class="btn" style="width:100%;padding:14px;background:var(--inv);color:var(--invt);border-radius:12px;font-weight:800;font-size:15px" onclick="closeModal()">
          Continue Shopping
        </button>
      </div>
    </div>
  `;

    selectedReviewStars = 5;
    updateReviewStarDisplay();
}

function renderOrderConfirmation(orderData, customerName, items) {
    openOrderConfirmationModal(orderData, customerName, items);
}

function setReviewStars(n) {
    selectedReviewStars = n;
    updateReviewStarDisplay();
}

function updateReviewStarDisplay() {
    const container = document.getElementById('order-stars');
    if (!container) return;
    const spans = container.querySelectorAll('span');
    spans.forEach((s, idx) => {
        s.className = (idx < selectedReviewStars) ? 'star-on' : 'star-off';
    });
}

async function submitOrderReview(productId, customerName, orderId) {
    const commentEl = document.getElementById('order-review-comment');
    const feedback = document.getElementById('review-feedback');
    const comment = commentEl ? commentEl.value.trim() : '';

    if (!comment) {
        if (feedback) { feedback.style.color = 'var(--err)'; feedback.textContent = 'Please type a short review.'; }
        return;
    }

    try {
        const params = new URLSearchParams({
            id: productId,
            name: customerName,
            stars: selectedReviewStars,
            comment: comment,
            orderId: orderId || getReviewOrderId(productId)
        });

        const res = await fetch('/api/reviews', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params.toString()
        });

        if (res.ok) {
            if (feedback) { feedback.style.color = 'var(--fg)'; feedback.textContent = '✓ Thank you! Your review has been posted.'; }
            if (commentEl) commentEl.disabled = true;
            await loadCatalog(); // Refresh review counts and stars across catalog!
        } else {
            if (feedback) { feedback.style.color = 'var(--err)'; feedback.textContent = 'Could not post review.'; }
        }
    } catch (err) {
        if (feedback) { feedback.style.color = 'var(--err)'; feedback.textContent = err.message; }
    }
}

// --- Main Page Sign-In Portal Modal ---
function openPortalModal() {
    closePortalModal();
    const c = document.getElementById('portal-modal-container');
    if (!c) return;

    const modal = document.createElement('div');
    modal.id = 'portal-modal-instance';
    modal.className = 'mb';
    modal.onclick = (e) => { if (e.target === modal) closePortalModal(); };

    modal.innerHTML = `
    <div class="portal-card-main">
      <button class="x" onclick="closePortalModal()">✕</button>
      <div style="font-weight:900;letter-spacing:0.18em;font-size:18px;margin-bottom:20px;text-align:center">
        SWIFTMART PORTAL
      </div>

      <div class="portal-tabs">
        <div class="portal-tab active" id="ptab-cust" onclick="switchMainPortalTab('cust')">Customer</div>
        <div class="portal-tab" id="ptab-admin" onclick="switchMainPortalTab('admin')">Staff / Admin</div>
      </div>

      <!-- Customer Portal View -->
      <div id="pview-cust">
        <h3 style="font-size:18px;font-weight:800;margin-bottom:6px">Customer Access</h3>
        <p style="color:var(--mut);font-size:13px;line-height:1.5;margin-bottom:20px">
          You are currently browsing as a guest shopper. Enjoy an automatic 5% store discount on every purchase with free nationwide shipping.
        </p>
        <button class="btn" style="width:100%" onclick="closePortalModal()">Continue Shopping</button>
      </div>

      <!-- Staff / Admin Portal View -->
      <div id="pview-admin" style="display:none">
        <h3 style="font-size:18px;font-weight:800;margin-bottom:6px">Admin Authentication</h3>
        <p style="color:var(--mut);font-size:13px;line-height:1.5;margin-bottom:16px">
          Enter administrative password to manage inventory, stock file exports/imports, and audit receipts.
        </p>
        <form onsubmit="handleMainAdminAuth(event)">
          <div style="margin-bottom:14px">
            <label style="margin-bottom:4px">Password
              <input type="password" id="main-admin-pass" required placeholder="Enter administrative password" autofocus>
            </label>
            <div class="err" id="main-auth-err"></div>
          </div>
          <button type="submit" class="btn" style="width:100%" id="main-auth-btn">Authenticate &amp; Open Admin</button>
        </form>
      </div>
    </div>
  `;
    c.appendChild(modal);
}

function closePortalModal() {
    const el = document.getElementById('portal-modal-instance');
    if (el) el.remove();
}

function switchMainPortalTab(tab) {
    const isCust = tab === 'cust';
    document.getElementById('ptab-cust').classList.toggle('active', isCust);
    document.getElementById('ptab-admin').classList.toggle('active', !isCust);
    document.getElementById('pview-cust').style.display = isCust ? 'block' : 'none';
    document.getElementById('pview-admin').style.display = isCust ? 'none' : 'block';
    if (!isCust) {
        setTimeout(() => document.getElementById('main-admin-pass')?.focus(), 100);
    }
}

async function handleMainAdminAuth(e) {
    e.preventDefault();
    const pass = document.getElementById('main-admin-pass').value.trim();
    const errEl = document.getElementById('main-auth-err');
    const btn = document.getElementById('main-auth-btn');

    btn.disabled = true;
    btn.textContent = 'Verifying...';

    try {
        const res = await fetch('/api/admin/orders', {
            headers: { 'X-Admin': pass }
        });

        if (res.ok) {
            sessionStorage.setItem('swiftmart_admin', pass);
            window.location.href = '/admin';
        } else {
            errEl.textContent = 'Invalid administrator credentials. Access denied.';
            document.getElementById('main-admin-pass').value = '';
        }
    } catch (err) {
        errEl.textContent = 'Network error: ' + err.message;
    } finally {
        btn.disabled = false;
        btn.textContent = 'Authenticate & Open Admin';
    }
}

function toast(msg) {
    const el = document.getElementById('toast');
    if (!el) return;
    el.textContent = msg;
    el.classList.add('on');
    setTimeout(() => el.classList.remove('on'), 3000);
}
function showToast(msg) {
    toast(msg);
}

document.addEventListener('DOMContentLoaded', init);