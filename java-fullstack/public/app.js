const state = {
  vehicles: [],
  bookings: [],
  selectedVehicleId: null
};

const currency = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0
});

const el = {
  loginPage: document.getElementById("loginPage"),
  app: document.getElementById("app"),
  loginForm: document.getElementById("loginForm"),
  userId: document.getElementById("userId"),
  password: document.getElementById("password"),
  logoutButton: document.getElementById("logoutButton"),
  customerName: document.getElementById("customerName"),
  location: document.getElementById("location"),
  pickupDate: document.getElementById("pickupDate"),
  returnDate: document.getElementById("returnDate"),
  vehicleType: document.getElementById("vehicleType"),
  searchVehicle: document.getElementById("searchVehicle"),
  paymentMethod: document.getElementById("paymentMethod"),
  couponCode: document.getElementById("couponCode"),
  selectedVehicle: document.getElementById("selectedVehicle"),
  rentalDays: document.getElementById("rentalDays"),
  discountAmount: document.getElementById("discountAmount"),
  totalFare: document.getElementById("totalFare"),
  confirmBooking: document.getElementById("confirmBooking"),
  availabilityText: document.getElementById("availabilityText"),
  vehicleList: document.getElementById("vehicleList"),
  fleetGrid: document.getElementById("fleetGrid"),
  bookingTable: document.getElementById("bookingTable"),
  bookingHistory: document.getElementById("bookingHistory"),
  dashRevenue: document.getElementById("dashRevenue"),
  dashBookings: document.getElementById("dashBookings"),
  dashUtilization: document.getElementById("dashUtilization"),
  metricVehicles: document.getElementById("metricVehicles"),
  metricBookings: document.getElementById("metricBookings"),
  addVehicleForm: document.getElementById("addVehicleForm"),
  newVehicleName: document.getElementById("newVehicleName"),
  newVehicleType: document.getElementById("newVehicleType"),
  newVehicleLocation: document.getElementById("newVehicleLocation"),
  newVehicleRange: document.getElementById("newVehicleRange"),
  newVehiclePrice: document.getElementById("newVehiclePrice"),
  toast: document.getElementById("toast"),
  toastTitle: document.getElementById("toastTitle"),
  toastMessage: document.getElementById("toastMessage")
};

function todayIso() {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return now.toISOString().slice(0, 10);
}

function addDaysIso(dateIso, days) {
  const date = new Date(dateIso);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

function rentalDays() {
  const diff = Math.ceil((new Date(el.returnDate.value) - new Date(el.pickupDate.value)) / 86400000);
  return Math.max(diff, 1);
}

function overlaps(startA, endA, startB, endB) {
  return startA < endB && startB < endA;
}

function isAvailable(vehicleId) {
  return !state.bookings.some((booking) => {
    return booking.vehicleId === vehicleId &&
      booking.status !== "Cancelled" &&
      overlaps(el.pickupDate.value, el.returnDate.value, booking.pickup, booking.drop);
  });
}

function discountRate() {
  const code = el.couponCode.value.trim().toUpperCase();
  if (code === "GREEN10") return 0.1;
  if (code === "EV20") return 0.2;
  return 0;
}

function selectedVehicle() {
  return state.vehicles.find((vehicle) => vehicle.id === state.selectedVehicleId);
}

function fare(vehicle) {
  if (!vehicle) return { subtotal: 0, discount: 0, total: 0 };
  const subtotal = vehicle.price * rentalDays();
  const discount = Math.round(subtotal * discountRate());
  return { subtotal, discount, total: subtotal - discount };
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  if (!response.ok) {
    throw new Error(data.message || "Request failed");
  }
  return data;
}

function showToast(title, message) {
  el.toastTitle.textContent = title;
  el.toastMessage.textContent = message;
  el.toast.classList.remove("hidden");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => el.toast.classList.add("hidden"), 3200);
}

async function loadData() {
  state.vehicles = await api("/api/vehicles");
  state.bookings = await api("/api/bookings");
  renderAll();
}

function renderVehicles() {
  const query = el.searchVehicle.value.trim().toLowerCase();
  const filtered = state.vehicles.filter((vehicle) => {
    const matchesLocation = el.location.value === "All" || vehicle.location === el.location.value;
    const matchesType = el.vehicleType.value === "All" || vehicle.type === el.vehicleType.value;
    const matchesSearch = !query || [vehicle.name, vehicle.location, vehicle.type].join(" ").toLowerCase().includes(query);
    return matchesLocation && matchesType && matchesSearch;
  });

  el.availabilityText.textContent = filtered.length + (filtered.length === 1 ? " EV found" : " EVs found");

  el.vehicleList.innerHTML = filtered.map((vehicle) => {
    const available = isAvailable(vehicle.id);
    return `
      <article class="vehicle-card">
        <div class="vehicle-image" style="background-image: url('${vehicle.image}')">
          <span class="badge ${available ? "" : "danger"}">${available ? vehicle.status : "Booked"}</span>
        </div>
        <div class="vehicle-body">
          <div class="vehicle-title">
            <div><h3>${vehicle.name}</h3><span class="muted">${vehicle.type} | ${vehicle.location}</span></div>
            <div class="price">${currency.format(vehicle.price)}/day</div>
          </div>
          <div class="specs">
            <div class="spec"><strong>${vehicle.range} km</strong><span>Range</span></div>
            <div class="spec"><strong>${vehicle.battery}%</strong><span>Battery</span></div>
            <div class="spec"><strong>${vehicle.seats}</strong><span>Seats</span></div>
          </div>
          <div class="card-actions">
            <button class="button primary" data-select="${vehicle.id}" ${available ? "" : "disabled"}>Select EV</button>
            <button class="button" data-invoice="${vehicle.id}">Invoice</button>
          </div>
        </div>
      </article>
    `;
  }).join("");

  document.querySelectorAll("[data-select]").forEach((button) => {
    button.addEventListener("click", () => {
      state.selectedVehicleId = button.dataset.select;
      updateSummary();
      showToast("EV selected", selectedVehicle().name + " selected.");
    });
  });

  document.querySelectorAll("[data-invoice]").forEach((button) => {
    button.addEventListener("click", () => {
      const vehicle = state.vehicles.find((item) => item.id === button.dataset.invoice);
      showToast("Invoice preview", vehicle.name + " estimate is " + currency.format(fare(vehicle).total) + ".");
    });
  });

  if (!filtered.some((vehicle) => vehicle.id === state.selectedVehicleId)) {
    state.selectedVehicleId = null;
  }

  updateSummary();
}

function renderFleet() {
  el.fleetGrid.innerHTML = state.vehicles.map((vehicle) => `
    <article class="fleet-card">
      <h3>${vehicle.name}</h3>
      <p class="muted">${vehicle.location} | ${vehicle.type}</p>
      <div class="battery"><span style="width: ${vehicle.battery}%"></span></div>
      <p class="muted">Battery: ${vehicle.battery}% | Range: ${vehicle.range} km | Service: ${vehicle.maintenance}</p>
    </article>
  `).join("");
}

function renderDashboard() {
  const active = state.bookings.filter((booking) => booking.status !== "Cancelled");
  const revenue = active.reduce((sum, booking) => sum + booking.amount, 0);
  const utilization = state.vehicles.length ? Math.round((new Set(active.map((booking) => booking.vehicleId)).size / state.vehicles.length) * 100) : 0;

  el.dashRevenue.textContent = currency.format(revenue);
  el.dashBookings.textContent = active.length;
  el.dashUtilization.textContent = utilization + "%";
  el.metricVehicles.textContent = state.vehicles.length;
  el.metricBookings.textContent = state.bookings.length;

  el.bookingTable.innerHTML = state.bookings.map((booking) => `
    <div class="table-row">
      <strong>${booking.id}</strong>
      <span>${booking.vehicleName}</span>
      <span>${currency.format(booking.amount)}</span>
      <span class="badge ${booking.status === "Cancelled" ? "danger" : ""}">${booking.status}</span>
      <button class="button danger" data-cancel="${booking.id}" ${booking.status === "Cancelled" ? "disabled" : ""}>Cancel</button>
    </div>
  `).join("");

  el.bookingHistory.innerHTML = state.bookings.slice().reverse().map((booking) => `
    <div class="history-item"><strong>${booking.customer}</strong><div class="muted">${booking.vehicleName} | ${booking.pickup} to ${booking.drop} | ${booking.payment} | ${booking.status}</div></div>
  `).join("");

  document.querySelectorAll("[data-cancel]").forEach((button) => {
    button.addEventListener("click", async () => {
      await api("/api/bookings/cancel", {
        method: "POST",
        body: JSON.stringify({ id: button.dataset.cancel })
      });
      showToast("Booking cancelled", button.dataset.cancel + " cancelled.");
      await loadData();
    });
  });
}

function updateSummary() {
  const vehicle = selectedVehicle();
  const currentFare = fare(vehicle);
  el.selectedVehicle.textContent = vehicle ? vehicle.name : "None";
  el.rentalDays.textContent = rentalDays();
  el.discountAmount.textContent = currency.format(currentFare.discount);
  el.totalFare.textContent = currency.format(currentFare.total);
}

function renderAll() {
  renderVehicles();
  renderFleet();
  renderDashboard();
}

async function confirmBooking() {
  const vehicle = selectedVehicle();
  if (!vehicle) {
    showToast("Select an EV", "Choose an available vehicle before booking.");
    return;
  }

  if (!isAvailable(vehicle.id)) {
    showToast("Booking blocked", "This EV is already reserved for those dates.");
    await loadData();
    return;
  }

  await api("/api/bookings", {
    method: "POST",
    body: JSON.stringify({
      customer: el.customerName.value.trim() || "Guest Customer",
      vehicleId: vehicle.id,
      pickup: el.pickupDate.value,
      drop: el.returnDate.value,
      payment: el.paymentMethod.value,
      coupon: el.couponCode.value
    })
  });

  state.selectedVehicleId = null;
  showToast("Booking confirmed", "Java backend created the booking.");
  await loadData();
}

async function addVehicle(event) {
  event.preventDefault();
  await api("/api/vehicles", {
    method: "POST",
    body: JSON.stringify({
      name: el.newVehicleName.value,
      type: el.newVehicleType.value,
      location: el.newVehicleLocation.value,
      range: Number(el.newVehicleRange.value),
      price: Number(el.newVehiclePrice.value)
    })
  });
  el.addVehicleForm.reset();
  showToast("Vehicle added", "Java backend added the EV.");
  await loadData();
}

function setupDates() {
  const today = todayIso();
  el.pickupDate.min = today;
  el.returnDate.min = addDaysIso(today, 1);
  el.pickupDate.value = today;
  el.returnDate.value = addDaysIso(today, 2);
}

el.loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await api("/api/login", {
      method: "POST",
      body: JSON.stringify({ userId: el.userId.value, password: el.password.value })
    });
    el.loginPage.classList.add("hidden");
    el.app.classList.remove("hidden");
    showToast("Login successful", "Java backend accepted your credentials.");
    await loadData();
  } catch (error) {
    showToast("Login failed", error.message);
  }
});

el.logoutButton.addEventListener("click", () => {
  el.app.classList.add("hidden");
  el.loginPage.classList.remove("hidden");
});

[el.location, el.pickupDate, el.returnDate, el.vehicleType, el.paymentMethod, el.couponCode].forEach((field) => {
  field.addEventListener("change", () => {
    if (el.returnDate.value <= el.pickupDate.value) {
      el.returnDate.value = addDaysIso(el.pickupDate.value, 1);
    }
    el.returnDate.min = addDaysIso(el.pickupDate.value, 1);
    renderAll();
  });
});

el.searchVehicle.addEventListener("input", renderAll);
el.confirmBooking.addEventListener("click", confirmBooking);
el.addVehicleForm.addEventListener("submit", addVehicle);

setupDates();
