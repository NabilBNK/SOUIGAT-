import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";

const baseUrl = process.env.SNAPSHOT_BASE_URL || "http://127.0.0.1:5173";
const outputDir = process.env.SNAPSHOT_OUTPUT_DIR || path.resolve(process.cwd(), "..", "app_snapshots");

const nowIso = new Date().toISOString().replace(/[:.]/g, "-");

const publicRoutes = [
  "/login",
  "/unauthorized",
  "/this-route-does-not-exist",
];

const protectedRoutes = [
  "/",
  "/office",
  "/office/trips",
  "/office/trips/new",
  "/office/trips/1",
  "/office/tickets",
  "/office/reports",
  "/cargo",
  "/cargo/tickets/1",
  "/admin",
  "/admin/users",
  "/admin/buses",
  "/admin/offices",
  "/admin/pricing",
  "/admin/settlements",
  "/admin/audit",
  "/admin/quarantine",
];

function routeToFileName(route) {
  if (route === "/") return "root";
  return route.replace(/^\//, "").replace(/[/:?&=]+/g, "_");
}

function mockUser() {
  return {
    id: 1,
    phone: "0661111111",
    first_name: "Admin",
    last_name: "User",
    role: "admin",
    department: "all",
    office: 1,
    office_name: "Alger Centre",
    office_city: "Algiers",
    permissions: ["all"],
    is_active: true,
  };
}

function mockTrip(id = 1) {
  return {
    id,
    route_name: "Alger -> Oran",
    origin: "Alger",
    destination: "Oran",
    departure_datetime: new Date().toISOString(),
    arrival_datetime: new Date(Date.now() + 5 * 3600 * 1000).toISOString(),
    status: "in_progress",
    office_scope_ids: [1],
    office_id: 1,
    office_name: "Alger Centre",
    conductor_id: 1,
    conductor_name: "Admin User",
    bus_id: 1,
    bus_plate: "12345-16",
    is_active: true,
  };
}

function paginated(results = []) {
  return {
    count: results.length,
    next: null,
    previous: null,
    results,
  };
}

async function setupMockApi(page) {
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const p = url.pathname;
    const method = request.method();

    if (!p.startsWith("/api/")) {
      return route.continue();
    }

    if (p === "/api/auth/me/") {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockUser()) });
    }

    if (p === "/api/auth/token/refresh/") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ access: "mock_access", refresh: "mock_refresh", strategy: "rotated" }),
      });
    }

    if (p === "/api/trips/reference-data/") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          offices: [{ id: 1, name: "Alger Centre", city: "Algiers" }],
          buses: [{ id: 1, plate_number: "12345-16", capacity: 50, is_active: true }],
          conductors: [mockUser()],
        }),
      });
    }

    if (p === "/api/trips/") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(paginated([mockTrip(1), mockTrip(2)])),
      });
    }

    if (/^\/api\/trips\/\d+\/$/.test(p)) {
      const id = Number(p.split("/")[3] || 1);
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockTrip(id)) });
    }

    if (p === "/api/tickets/") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(paginated([])),
      });
    }

    if (p === "/api/cargo/") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(paginated([])),
      });
    }

    if (/^\/api\/cargo\/\d+\/$/.test(p)) {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: 1,
          trip: 1,
          ticket_number: "CARGO-001",
          sender_name: "Ali",
          receiver_name: "Omar",
          cargo_tier: "small",
          status: "created",
          price: 1000,
          payment_source: "cash",
          created_at: new Date().toISOString(),
        }),
      });
    }

    if (p === "/api/expenses/") {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(paginated([])) });
    }

    if (p === "/api/reports/daily/") {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([]) });
    }

    if (/^\/api\/reports\/trip\/\d+\/$/.test(p)) {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ trip_id: 1, totals: {}, passengers: [], cargo: [], expenses: [] }),
      });
    }

    if (p.startsWith("/api/settlements")) {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(paginated([])) });
    }

    if (p.startsWith("/api/admin/users/")) {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(paginated([mockUser()])) });
    }

    if (p.startsWith("/api/admin/buses/")) {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(paginated([{ id: 1, plate_number: "12345-16", capacity: 50, is_active: true }])),
      });
    }

    if (p.startsWith("/api/admin/offices/")) {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(paginated([{ id: 1, name: "Alger Centre", city: "Algiers" }])),
      });
    }

    if (p.startsWith("/api/admin/pricing/")) {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(paginated([])) });
    }

    if (p.startsWith("/api/admin/audit-log/")) {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(paginated([])) });
    }

    if (p.startsWith("/api/quarantine/")) {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(paginated([])) });
    }

    if (method === "GET") {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({}) });
    }

    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ ok: true }) });
  });
}

async function captureRoute(page, route, prefix) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1500);
  const fileName = `${prefix}_${routeToFileName(route)}.png`;
  await page.screenshot({ path: path.join(outputDir, fileName), fullPage: true });
  console.log(`Saved ${fileName}`);
}

async function main() {
  await fs.mkdir(outputDir, { recursive: true });

  const browser = await chromium.launch({ headless: true });

  try {
    const publicContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const publicPage = await publicContext.newPage();
    await setupMockApi(publicPage);

    for (const route of publicRoutes) {
      await captureRoute(publicPage, route, `public_${nowIso}`);
    }
    await publicContext.close();

    const authContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await authContext.addInitScript(() => {
      window.localStorage.setItem("souigat_access", "mock_access");
      window.localStorage.setItem("souigat_refresh", "mock_refresh");
    });
    const authPage = await authContext.newPage();
    await setupMockApi(authPage);

    for (const route of protectedRoutes) {
      await captureRoute(authPage, route, `app_${nowIso}`);
    }
    await authContext.close();
  } finally {
    await browser.close();
  }

  console.log(`Snapshots complete in ${outputDir}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
