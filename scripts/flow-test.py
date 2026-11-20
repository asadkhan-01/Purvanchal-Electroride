#!/usr/bin/env python3
"""End-to-end role/flow check against a running Electroride API (test data only).

Creates branch managers and salespeople, registers vehicles, creates enquiries,
and walks the enquiry -> booking -> quotation -> confirmation and inventory
transfer flows, checking every role restriction along the way.

Usage:
  BASE_URL=https://example.onrender.com ADMIN_USER=admin ADMIN_PASSWORD=... \
  TEST_PASSWORD=... python3 scripts/flow-test.py

TEST_PASSWORD (12+ chars) is used for every account the script creates.
Re-running is safe: existing accounts are reused, and vehicles/enquiries get a
run-specific suffix so they never collide.
"""
import base64, json, os, ssl, sys, time, urllib.error, urllib.request

BASE = os.environ["BASE_URL"].rstrip("/")
ADMIN = (os.environ.get("ADMIN_USER", "admin"), os.environ["ADMIN_PASSWORD"])
PW = os.environ["TEST_PASSWORD"]
assert len(PW) >= 12, "TEST_PASSWORD must be at least 12 characters"
RUN = time.strftime("%m%d%H%M")
ctx = ssl.create_default_context(cafile=os.environ.get("SSL_CERT_FILE") or None)

results = []

def call(who, method, path, body=None):
    req = urllib.request.Request(BASE + path, method=method,
        data=None if body is None else json.dumps(body).encode())
    req.add_header("Content-Type", "application/json")
    token = base64.b64encode(f"{who[0]}:{who[1]}".encode()).decode()
    req.add_header("Authorization", "Basic " + token)
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=90) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, None

def check(label, who, method, path, body=None, expect=(200, 201)):
    expect = (expect,) if isinstance(expect, int) else expect
    status, data = call(who, method, path, body)
    ok = status in expect
    results.append(ok)
    print(f"{'PASS' if ok else 'FAIL'}  [{who[0]:>14}] {label}: {status} (expected {'/'.join(map(str, expect))})")
    return data

def count(label, who, path, expected):
    status, data = call(who, "GET", path)
    n = len(data) if isinstance(data, list) else -1
    ok = status == 200 and n == expected
    results.append(ok)
    print(f"{'PASS' if ok else 'FAIL'}  [{who[0]:>14}] {label}: {n} (expected {expected})")
    return data

admin = ADMIN
print(f"== Target {BASE} · run {RUN}")
me = check("admin identity", admin, "GET", "/api/v1/auth/me")

print("\n== 1. Accounts (admin only)")
accounts = [
    ("mgr.varanasi", "Varanasi Manager", "BRANCH_MANAGER", "Varanasi"),
    ("sales.asha", "Asha Verma", "SALESPERSON", "Varanasi"),
    ("sales.ravi", "Ravi Yadav", "SALESPERSON", "Varanasi"),
    ("mgr.ghazipur", "Ghazipur Manager", "BRANCH_MANAGER", "Ghazipur"),
    ("sales.imran", "Imran Khan", "SALESPERSON", "Ghazipur"),
]
for username, name, role, branch in accounts:
    check(f"create {role} {username}", admin, "POST", "/api/v1/users",
          {"username": username, "password": PW, "displayName": name, "role": role, "branch": branch},
          expect=(201, 409))
mv, asha, ravi, mg, imran = [(a[0], PW) for a in accounts]
check("manager cannot list users", mv, "GET", "/api/v1/users", expect=403)
check("salesperson cannot create users", asha, "POST", "/api/v1/users",
      {"username": "x.hack", "password": PW, "displayName": "X", "role": "ADMIN"}, expect=403)
for who, n in [(admin, None), (mv, 2), (asha, 1)]:
    if n is not None:
        count("salespeople picker", who, "/api/v1/users/salespeople", n)

print("\n== 2. Inventory registration")
def vehicle(chassis, model, variant, colour, branch):
    return {"chassis": chassis, "model": model, "variant": variant, "colour": colour, "branch": branch,
            "motorSerial": f"MTR-{chassis}", "batterySerial": f"BAT-{chassis}",
            "chargerSerial": f"CHG-{chassis}", "controllerSerial": f"CTL-{chassis}"}
V = lambda n: f"VNS-{RUN}-{n}"
G = lambda n: f"GZP-{RUN}-{n}"
check("register 3 Varanasi vehicles", admin, "POST", "/api/v1/inventory", vehicle(V(1), "City Glide", "Standard", "White", "Varanasi"))
check("  …", admin, "POST", "/api/v1/inventory", vehicle(V(2), "City Glide", "Long Range", "Blue", "Varanasi"))
check("  …", admin, "POST", "/api/v1/inventory", vehicle(V(3), "Cargo Pro", "Loader", "Grey", "Varanasi"))
check("manager registers own-branch vehicle", mv, "POST", "/api/v1/inventory", vehicle(V(4), "City Glide", "Premium", "Black", "Varanasi"))
check("manager registers Ghazipur vehicle", mg, "POST", "/api/v1/inventory", vehicle(G(1), "City Glide", "Standard", "Red", "Ghazipur"))
check("manager cannot register in other branch", mv, "POST", "/api/v1/inventory", vehicle(G(9), "X", "X", "X", "Ghazipur"), expect=403)
check("salesperson cannot register", asha, "POST", "/api/v1/inventory", vehicle(V(9), "X", "X", "X", "Varanasi"), expect=403)
check("duplicate chassis rejected", admin, "POST", "/api/v1/inventory", vehicle(V(1), "X", "X", "X", "Varanasi"), expect=409)

print("\n== 3. Enquiries")
def enquiry(name, phone, branch, sp, model, source="Walk-in"):
    return {"customerName": name, "phone": phone, "branch": branch, "salesperson": sp,
            "source": source, "interestedModel": model, "notes": f"flow test {RUN}"}
e1 = check("manager creates enquiry for Asha", mv, "POST", "/api/v1/enquiries", enquiry(f"Test Customer A {RUN}", "9000000101", "Varanasi", "Asha Verma", "City Glide"))
e2 = check("Asha creates own enquiry", asha, "POST", "/api/v1/enquiries", enquiry(f"Test Customer B {RUN}", "9000000102", "Varanasi", "Asha Verma", "Cargo Pro", "Phone"))
e3 = check("Ravi creates own enquiry", ravi, "POST", "/api/v1/enquiries", enquiry(f"Test Customer C {RUN}", "9000000103", "Varanasi", "Ravi Yadav", "City Glide", "Website"))
e4 = check("admin creates Ghazipur enquiry for Imran", admin, "POST", "/api/v1/enquiries", enquiry(f"Test Customer D {RUN}", "9000000104", "Ghazipur", "Imran Khan", "City Glide", "Referral"))
check("Asha cannot assign to Ravi", asha, "POST", "/api/v1/enquiries", enquiry("X", "9000000199", "Varanasi", "Ravi Yadav", "X"), expect=403)
check("Varanasi manager cannot create in Ghazipur", mv, "POST", "/api/v1/enquiries", enquiry("X", "9000000198", "Ghazipur", "Imran Khan", "X"), expect=403)
check("invalid phone rejected", admin, "POST", "/api/v1/enquiries", enquiry("X", "123", "Varanasi", "Asha Verma", "X"), expect=400)
check("Ravi cannot see Asha's enquiry", ravi, "GET", f"/api/v1/enquiries/{e1['id']}", expect=404)
check("Ghazipur manager cannot see Varanasi enquiry", mg, "GET", f"/api/v1/enquiries/{e1['id']}", expect=404)
check("Asha moves enquiry to Contacted", asha, "PATCH", f"/api/v1/enquiries/{e1['id']}/stage", {"stage": "Contacted"})
check("Asha sets follow-up date", asha, "PATCH", f"/api/v1/enquiries/{e1['id']}/follow-up", {"followUpDate": time.strftime("%Y-%m-%d")})
check("Asha logs call activity", asha, "POST", f"/api/v1/enquiries/{e1['id']}/activities", {"message": "Called customer; wants test ride"})
check("Asha moves enquiry to Interested", asha, "PATCH", f"/api/v1/enquiries/{e1['id']}/stage", {"stage": "Interested"})
check("activity history recorded", asha, "GET", f"/api/v1/enquiries/{e1['id']}/activities")
check("Ravi marks his enquiry Lost", ravi, "PATCH", f"/api/v1/enquiries/{e3['id']}/stage", {"stage": "Lost"})

print("\n== 4. Bookings and quotations")
b1 = check("Asha books enquiry A on V1", asha, "POST", "/api/v1/bookings", {"enquiryId": e1["id"], "chassis": V(1)})
check("second booking for same chassis blocked", ravi, "POST", "/api/v1/bookings",
      {"chassis": V(1), "customerName": "Walk In", "customerPhone": "9000000201"}, expect=(409,))
b2 = check("Ravi direct booking on V2", ravi, "POST", "/api/v1/bookings",
      {"chassis": V(2), "customerName": f"Walk-in Customer {RUN}", "customerPhone": "9000000202"})
check("Imran cannot book Varanasi stock", imran, "POST", "/api/v1/bookings",
      {"chassis": V(3), "customerName": "X", "customerPhone": "9000000203"}, expect=(403, 404))
check("Asha saves draft quotation", asha, "POST", f"/api/v1/bookings/{b1['id']}/quotation",
      {"vehiclePrice": "95000.00", "discount": "2000.00", "accessories": "1500.00", "otherCharges": "500.00"})
check("Asha cannot approve quotation", asha, "POST", f"/api/v1/bookings/{b1['id']}/quotation/approve", expect=403)
check("Asha cannot confirm booking", asha, "POST", f"/api/v1/bookings/{b1['id']}/confirm", expect=403)
check("Ghazipur manager cannot confirm Varanasi booking", mg, "POST", f"/api/v1/bookings/{b1['id']}/confirm", expect=(403, 404))
check("manager approves quotation", mv, "POST", f"/api/v1/bookings/{b1['id']}/quotation/approve")
check("manager confirms booking", mv, "POST", f"/api/v1/bookings/{b1['id']}/confirm")
status, enq = call(asha, "GET", f"/api/v1/enquiries/{e1['id']}")
ok = enq and enq.get("stage") == "Booked"; results.append(ok)
print(f"{'PASS' if ok else 'FAIL'}  [{'asha':>14}] enquiry stage after confirmation: {enq and enq.get('stage')} (expected Booked)")
check("manager cancels Ravi's direct booking", mv, "POST", f"/api/v1/bookings/{b2['id']}/cancel", {"reason": "Customer withdrew (flow test)"})
status, v2 = call(mv, "GET", f"/api/v1/inventory/{V(2)}")
ok = v2 and v2.get("status") == "AVAILABLE"; results.append(ok)
print(f"{'PASS' if ok else 'FAIL'}  [{'mgr.varanasi':>14}] V2 released after cancel: {v2 and v2.get('status')} (expected AVAILABLE)")
check("booking audit trail", mv, "GET", f"/api/v1/bookings/{b1['id']}/events")
check("Imran cannot see Varanasi booking", imran, "GET", f"/api/v1/bookings/{b1['id']}", expect=404)
check("Ravi cannot book Asha's enquiry", ravi, "POST", "/api/v1/bookings", {"enquiryId": e2["id"], "chassis": V(3)}, expect=(403, 404))

print("\n== 5. Reservation and branch transfer")
check("manager reserves V3", mv, "POST", f"/api/v1/inventory/{V(3)}/reserve")
check("manager releases V3", mv, "POST", f"/api/v1/inventory/{V(3)}/release")
check("salesperson cannot reserve", asha, "POST", f"/api/v1/inventory/{V(3)}/reserve", expect=403)
check("manager transfers V4 to Ghazipur", mv, "POST", f"/api/v1/inventory/{V(4)}/transfer", {"destination": "Ghazipur"})
check("source manager cannot receive", mv, "POST", f"/api/v1/inventory/{V(4)}/receive", expect=(403, 409))
check("Ghazipur manager receives V4", mg, "POST", f"/api/v1/inventory/{V(4)}/receive")
check("movement history", mg, "GET", f"/api/v1/inventory/{V(4)}/movements")
check("booked V1 cannot be released", mv, "POST", f"/api/v1/inventory/{V(1)}/release", expect=409)

print("\n== 6. What each role sees (this run's records)")
for who, label in [(admin, "admin"), (mv, "Varanasi manager"), (asha, "Asha"), (ravi, "Ravi"), (mg, "Ghazipur manager"), (imran, "Imran")]:
    _, enqs = call(who, "GET", "/api/v1/enquiries")
    _, vehs = call(who, "GET", "/api/v1/inventory")
    _, bks = call(who, "GET", "/api/v1/bookings")
    mine = lambda xs, key: [x for x in (xs or []) if RUN in str(x.get(key, ""))]
    print(f"      {label:>17}: enquiries={len(mine(enqs, 'notes'))} vehicles={len(mine(vehs, 'chassis'))} bookings={len(bks or [])} (total visible)")

passed = sum(results)
print(f"\n== {passed}/{len(results)} checks passed")
sys.exit(0 if passed == len(results) else 1)
