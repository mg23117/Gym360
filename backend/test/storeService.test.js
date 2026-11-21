"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { createStoreService, normalizeItems } = require("../src/storeService");
const FV = { serverTimestamp: () => "2026-11-20T10:00:00Z" };
function fakeDb(initial) {
    const data = structuredClone(initial);
    const ref = path => ({ path, collection(name) { return { doc: id => ref(`${path}/${name}/${id}`) } } });
    return {
        data, doc: ref, async runTransaction(fn) {
            const ops = [];
            const tx = {
                async get(r) { let v = data[r.path]; return { id: r.path.split("/").at(-1), ref: r, exists: !!v, data: () => structuredClone(v) }; },
                set(r, value, options) { ops.push({ kind: "set", r, value, options }); },
                create(r, value) { ops.push({ kind: "create", r, value }); },
                update(r, value) { ops.push({ kind: "update", r, value }); }
            };
            const result = await fn(tx);
            for (const o of ops) {
                if (o.kind === "create" && data[o.r.path]) throw Error("exists");
                data[o.r.path] = o.kind === "set" && !o.options?.merge ? structuredClone(o.value) : { ...(data[o.r.path] || {}), ...structuredClone(o.value) };
            }
            return result;
        }
    };
}
const initial = () => ({
    "users/customer01": { uid: "customer01", fullName: "Usuario Prueba" },
    "admin_users/admin001": { active: true },
    "wallets/customer01": { balanceCents: 10000 },
    "products/product001": { name: "Agua embotellada", category: "Bebidas", priceCents: 150, stock: 12, isActive: true, imageUrls: [] },
    "products/product002": { name: "Toalla deportiva", category: "Accesorios", priceCents: 500, stock: 2, isActive: true, imageUrls: [] }
});
const items = [{ productId: "product001", quantity: 2 }, { productId: "product002", quantity: 1 }];
test("la compra descuenta stock y saldo en la misma transacción", async () => {
    const db = fakeDb(initial()), s = createStoreService(db, FV);
    const r = await s.checkout("customer01", { operationId: "purchase001", items });
    assert.equal(r.totalCents, 800); assert.equal(r.balanceCents, 9200);
    assert.equal(db.data["products/product001"].stock, 10);
    assert.equal(db.data["products/product002"].stock, 1);
    assert.equal(db.data["orders/purchase001"].status, "PENDING");
    assert.equal(db.data["wallets/customer01/transactions/purchase001"].deltaCents, -800);
});
test("el reintento con el mismo ID no vuelve a cobrar", async () => {
    const db = fakeDb(initial()), s = createStoreService(db, FV);
    await s.checkout("customer01", { operationId: "purchase001", items });
    const r = await s.checkout("customer01", { operationId: "purchase001", items });
    assert.equal(r.repeated, true); assert.equal(db.data["wallets/customer01"].balanceCents, 9200);
});
test("no hay descuento si falta stock", async () => {
    const db = fakeDb(initial()), s = createStoreService(db, FV);
    await assert.rejects(s.checkout("customer01", { operationId: "purchase003", items: [{ productId: "product002", quantity: 3 }] }), { code: "OUT_OF_STOCK" });
    assert.equal(db.data["wallets/customer01"].balanceCents, 10000); assert.ok(!db.data["orders/purchase003"]);
});
test("no hay descuento si el saldo es insuficiente", async () => {
    const db = fakeDb({ ...initial(), "wallets/customer01": { balanceCents: 100 } }), s = createStoreService(db, FV);
    await assert.rejects(s.checkout("customer01", { operationId: "purchase004", items }), { code: "INSUFFICIENT_BALANCE" });
    assert.equal(db.data["products/product001"].stock, 12);
});
test("un cliente no registra ventas presenciales ni ajusta stock", async () => {
    const s = createStoreService(fakeDb(initial()), FV);
    await assert.rejects(s.counterSale("customer01", { targetUid: "customer01", operationId: "sale00001", paymentMethod: "CASH", items }), { code: "ADMIN_REQUIRED" });
    await assert.rejects(s.stockAdjust("customer01", { productId: "product001", operationId: "adjust001", delta: 3, reason: "Compra mayorista" }), { code: "ADMIN_REQUIRED" });
});
test("venta presencial en efectivo descuenta existencias sin tocar billetera", async () => {
    const db = fakeDb(initial()), s = createStoreService(db, FV);
    const r = await s.counterSale("admin001", { targetUid: "customer01", operationId: "sale00001", paymentMethod: "CASH", items });
    assert.equal(r.status, "DELIVERED"); assert.equal(db.data["wallets/customer01"].balanceCents, 10000);
    assert.equal(db.data["products/product001"].stock, 10);
});
test("marcar listo, entregar y registrar hora", async () => {
    const db = fakeDb(initial()), s = createStoreService(db, FV);
    await s.checkout("customer01", { operationId: "purchase001", items });
    await s.transition("admin001", "purchase001", { action: "READY", operationId: "event00001", note: "Preparado" });
    await s.transition("admin001", "purchase001", { action: "DELIVERED", operationId: "event00002", note: "Entregado en recepción" });
    assert.equal(db.data["orders/purchase001"].status, "DELIVERED");
    assert.ok(db.data["orders/purchase001"].deliveredAt);
});
test("cancelar repone stock y devuelve dinero sin duplicar reembolso", async () => {
    const db = fakeDb(initial()), s = createStoreService(db, FV);
    await s.checkout("customer01", { operationId: "purchase001", items });
    await s.transition("admin001", "purchase001", { action: "CANCELLED", operationId: "event00001", note: "Pedido cancelado" });
    assert.equal(db.data["wallets/customer01"].balanceCents, 10000);
    assert.equal(db.data["products/product001"].stock, 12);
    const r = await s.transition("admin001", "purchase001", { action: "CANCELLED", operationId: "event00001", note: "Pedido cancelado" });
    assert.equal(r.repeated, true);
});
test("reembolso posterior a entrega no repone stock automáticamente", async () => {
    const db = fakeDb(initial()), s = createStoreService(db, FV);
    await s.checkout("customer01", { operationId: "purchase001", items });
    await s.transition("admin001", "purchase001", { action: "READY", operationId: "event00001", note: "Listo" });
    await s.transition("admin001", "purchase001", { action: "DELIVERED", operationId: "event00002", note: "Recibido" });
    await s.transition("admin001", "purchase001", { action: "REFUNDED", operationId: "event00003", note: "Producto defectuoso" });
    assert.equal(db.data["wallets/customer01"].balanceCents, 10000);
    assert.equal(db.data["products/product001"].stock, 10);
    assert.equal(db.data["orders/purchase001"].status, "REFUNDED");
});
test("carrito repite producto como una sola línea y limita cantidades", () => {
    assert.deepEqual(normalizeItems([{ productId: "product001", quantity: 1 }, { productId: "product001", quantity: 2 }]), [{ productId: "product001", quantity: 3 }]);
    assert.throws(() => normalizeItems([{ productId: "product001", quantity: 100 }]), { code: "INVALID_QUANTITY" });
});
