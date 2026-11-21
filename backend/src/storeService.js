"use strict";

// Todos los cobros, las ventas y los cambios en el stock se van a validar en el backend
const crypto = require("node:crypto");
const { ApiError } = require("./accountService");
const CATEGORIES = Object.freeze(["Suplementos", "Bebidas", "Ropa", "Accesorios", "Equipamiento", "Snacks", "Otros"]);
const allowedId = /^[A-Za-z0-9_-]{8,100}$/;
function fail(code, message, status = 400) { throw new ApiError(status, code, message); }
function id(value, field = "ID") { if (typeof value !== "string" || !allowedId.test(value)) fail("INVALID_ARGUMENT", `${field} inválido.`); return value; }
function uid(value) { if (typeof value !== "string" || !/^[A-Za-z0-9:_-]{6,128}$/.test(value)) fail("INVALID_ARGUMENT", "UID inválido."); return value; }
function cents(n, field = "Importe") { if (!Number.isSafeInteger(n) || n < 0 || n > 50000000) fail("INVALID_AMOUNT", `${field} inválido.`); return n; }
function qty(n) { if (!Number.isInteger(n) || n < 1 || n > 99) fail("INVALID_QUANTITY", "Cantidad inválida (1-99)."); return n; }
function normalizeItems(items) {
    if (!Array.isArray(items) || items.length < 1 || items.length > 30) fail("INVALID_CART", "El pedido debe tener entre 1 y 30 productos.");
    const map = new Map();
    for (const raw of items) {
        const productId = id(raw?.productId, "Producto");
        const amount = qty(raw?.quantity);
        const total = (map.get(productId) || 0) + amount;
        if (total > 99) fail("INVALID_QUANTITY", "Cantidad máxima 99 por producto.");
        map.set(productId, total);
    }
    if ([...map.values()].reduce((x, y) => x + y, 0) > 200) fail("INVALID_QUANTITY", "El pedido supera el máximo de unidades.");
    return [...map.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([productId, quantity]) => ({ productId, quantity }));
}
function fingerprint(value) { return crypto.createHash("sha256").update(JSON.stringify(value)).digest("hex"); }
function validProduct(data) {
    if (!data || data.isActive !== true || typeof data.name !== "string" || !data.name.trim()) fail("PRODUCT_UNAVAILABLE", "Producto no disponible.", 409);
    if (!CATEGORIES.includes(data.category)) fail("PRODUCT_INVALID", "Categoría inválida.", 409);
    cents(data.priceCents, "Precio");
    if (!Number.isSafeInteger(data.stock) || data.stock < 0 || data.stock > 1000000) fail("STOCK_INVALID", "Stock inconsistente.", 409);
    return data;
}
function makeLine(data, productId, quantity) {
    return {
        productId, name: data.name, category: data.category, quantity, unitPriceCents: data.priceCents,
        imageUrl: Array.isArray(data.imageUrls) && typeof data.imageUrls[0] === "string" ? data.imageUrls[0] : ""
    };
}
function createStoreService(db, FieldValue) {
    const checkAdmin = async (tx, actor) => {
        const snap = await tx.get(db.doc(`admin_users/${actor}`));
        if (!snap.exists) fail("ADMIN_REQUIRED", "Se requiere un administrador.", 403);
    };
    async function sale(actorUid, body, isCounter) {
        const actor = uid(actorUid);
        const target = isCounter ? uid(body?.targetUid) : actor;
        const operationId = id(body?.operationId, "operationId");
        const items = normalizeItems(body?.items);
        const paymentMethod = isCounter ? body?.paymentMethod : "WALLET";
        if (paymentMethod !== "WALLET" && !(isCounter && paymentMethod === "CASH")) fail("INVALID_PAYMENT", "Método de pago inválido.");
        const source = isCounter ? "COUNTER" : "APP";
        const expectedHash = fingerprint({ actor, target, source, paymentMethod, items });
        const adminRef = db.doc(`admin_users/${actor}`);
        const profileRef = db.doc(`users/${target}`);
        const orderRef = db.doc(`orders/${operationId}`);
        const walletRef = db.doc(`wallets/${target}`);
        const movementRef = walletRef.collection("transactions").doc(operationId);
        const productRefs = items.map(x => db.doc(`products/${x.productId}`));
        return db.runTransaction(async tx => {
            const [admin, profile, order, wallet, movement, ...products] = await Promise.all([
                tx.get(adminRef), tx.get(profileRef), tx.get(orderRef), tx.get(walletRef), tx.get(movementRef), ...productRefs.map(ref => tx.get(ref))
            ]);
            if (isCounter && !admin.exists) fail("ADMIN_REQUIRED", "Solo administradores registran ventas presenciales.", 403);
            if (!profile.exists || profile.data()?.uid !== target) fail("USER_NOT_FOUND", "No existe el perfil del cliente.", 404);
            if (order.exists) {
                const old = order.data();
                if (old.fingerprint !== expectedHash) fail("OPERATION_CONFLICT", "ID de operación utilizado con datos distintos.", 409);
                return {
                    orderId: operationId, status: old.status, totalCents: old.totalCents,
                    balanceCents: old.balanceAfterCents ?? null, repeated: true
                };
            }
            if (movement.exists) fail("OPERATION_CONFLICT", "El identificador ya se utilizó en otro movimiento.", 409);
            let totalCents = 0;
            const lines = [];
            for (let i = 0; i < items.length; i++) {
                if (!products[i].exists) fail("PRODUCT_NOT_FOUND", "Uno de los productos ya no existe.", 409);
                const data = validProduct(products[i].data());
                if (data.stock < items[i].quantity) fail("OUT_OF_STOCK", `Stock insuficiente: ${data.name}.`, 409);
                totalCents += data.priceCents * items[i].quantity;
                if (!Number.isSafeInteger(totalCents) || totalCents > 1_000_000_000) fail("AMOUNT_TOO_LARGE", "Importe demasiado grande.");
                lines.push(makeLine(data, items[i].productId, items[i].quantity));
            }
            const currentBalance = wallet.exists ? wallet.data()?.balanceCents : 0;
            if (!Number.isSafeInteger(currentBalance) || currentBalance < 0) fail("INVALID_BALANCE", "Saldo remoto inválido.", 409);
            if (paymentMethod === "WALLET" && currentBalance < totalCents) fail("INSUFFICIENT_BALANCE", "Saldo prepago insuficiente.", 409);
            const after = paymentMethod === "WALLET" ? currentBalance - totalCents : null;
            for (let i = 0; i < items.length; i++) {
                tx.update(productRefs[i], { stock: products[i].data().stock - items[i].quantity, updatedAt: FieldValue.serverTimestamp() });
            }
            if (paymentMethod === "WALLET") {
                tx.set(walletRef, { balanceCents: after, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
                tx.create(movementRef, {
                    type: "STORE_PURCHASE", actorUid: actor, deltaCents: -totalCents,
                    referenceId: operationId, orderId: operationId, balanceAfterCents: after, createdAt: FieldValue.serverTimestamp()
                });
            }
            tx.create(orderRef, {
                id: operationId, userUid: target, actorUid: actor, fingerprint: expectedHash,
                source, paymentMethod, items: lines, totalCents,
                status: isCounter ? "DELIVERED" : "PENDING", createdAt: FieldValue.serverTimestamp(),
                updatedAt: FieldValue.serverTimestamp(),
                ...(isCounter ? { deliveredAt: FieldValue.serverTimestamp(), deliveredByUid: actor, deliveryNote: "Venta presencial" } : {}),
                ...(paymentMethod === "WALLET" ? { balanceAfterCents: after } : {})
            });
            return {
                orderId: operationId, status: isCounter ? "DELIVERED" : "PENDING",
                totalCents, balanceCents: after, repeated: false
            };
        });
    }
    return {
        checkout(actorUid, body) { return sale(actorUid, body, false); },
        counterSale(actorUid, body) { return sale(actorUid, body, true); },
        async stockAdjust(actorUid, body) {
            const actor = uid(actorUid), productId = id(body?.productId, "Producto"), operationId = id(body?.operationId, "operationId");
            const delta = body?.delta;
            const reason = typeof body?.reason === "string" ? body.reason.trim() : "";
            if (!Number.isSafeInteger(delta) || delta === 0 || Math.abs(delta) > 100000) fail("INVALID_STOCK", "Ajuste de stock inválido.");
            if (reason.length < 3 || reason.length > 150) fail("INVALID_REASON", "Especifica el motivo del ajuste.");
            const productRef = db.doc(`products/${productId}`);
            const moveRef = productRef.collection("stock_movements").doc(operationId);
            return db.runTransaction(async tx => {
                const [admin, product, existing] = await Promise.all([tx.get(db.doc(`admin_users/${actor}`)), tx.get(productRef), tx.get(moveRef)]);
                if (!admin.exists) fail("ADMIN_REQUIRED", "Solo administradores pueden ajustar stock.", 403);
                if (!product.exists) fail("PRODUCT_NOT_FOUND", "No existe el producto.", 404);
                if (existing.exists) {
                    const p = existing.data();
                    if (p.actorUid !== actor || p.delta !== delta || p.reason !== reason) fail("OPERATION_CONFLICT", "ID usado en otro ajuste.", 409);
                    return { stock: p.stockAfter, repeated: true };
                }
                const before = product.data().stock;
                if (!Number.isSafeInteger(before) || before < 0 || before + delta < 0 || before + delta > 1000000) fail("INVALID_STOCK", "La existencia no puede ser negativa o exceder el máximo.", 409);
                const after = before + delta;
                tx.update(productRef, { stock: after, updatedAt: FieldValue.serverTimestamp() });
                tx.create(moveRef, { actorUid: actor, delta, reason, stockAfter: after, createdAt: FieldValue.serverTimestamp() });
                return { stock: after, repeated: false };
            });
        },
        async transition(actorUid, orderIdRaw, body) {
            const actor = uid(actorUid), orderId = id(orderIdRaw, "Pedido"), operationId = id(body?.operationId, "operationId");
            const action = body?.action;
            if (!["READY", "DELIVERED", "CANCELLED", "REFUNDED"].includes(action)) fail("INVALID_ACTION", "Transición inválida.");
            const note = typeof body?.note === "string" ? body.note.trim() : "";
            if (note.length > 300 || ((action === "REFUNDED" || action === "CANCELLED") && note.length < 3)) fail("INVALID_NOTE", "Indica un motivo de 3 a 300 caracteres.");
            const orderRef = db.doc(`orders/${orderId}`);
            const eventRef = orderRef.collection("events").doc(operationId);
            return db.runTransaction(async tx => {
                const [admin, order, event] = await Promise.all([tx.get(db.doc(`admin_users/${actor}`)), tx.get(orderRef), tx.get(eventRef)]);
                if (!admin.exists) fail("ADMIN_REQUIRED", "Solo administradores pueden gestionar pedidos.", 403);
                if (!order.exists) fail("ORDER_NOT_FOUND", "Pedido no encontrado.", 404);
                if (event.exists) {
                    const old = event.data();
                    if (old.action !== action || old.actorUid !== actor || old.note !== note) fail("OPERATION_CONFLICT", "Evento duplicado con otros datos.", 409);
                    return { orderId, status: old.statusAfter, repeated: true };
                }
                const data = order.data();
                const previous = data.status;
                const allowed = (action === "READY" && previous === "PENDING") ||
                    (action === "DELIVERED" && previous === "READY") ||
                    (action === "CANCELLED" && ["PENDING", "READY"].includes(previous)) ||
                    (action === "REFUNDED" && previous === "DELIVERED");
                if (!allowed) fail("INVALID_TRANSITION", `No se puede pasar de ${previous} a ${action}.`, 409);
                const reverseStock = action === "CANCELLED";
                const products = reverseStock ? await Promise.all(data.items.map(x => tx.get(db.doc(`products/${x.productId}`)))) : [];
                const needsRefund = (action === "CANCELLED" || action === "REFUNDED") && data.paymentMethod === "WALLET";
                const walletRef = db.doc(`wallets/${data.userUid}`);
                const refundRef = walletRef.collection("transactions").doc(operationId);
                const wallet = needsRefund ? await tx.get(walletRef) : null;
                const refund = needsRefund ? await tx.get(refundRef) : null;
                if (needsRefund && refund.exists) fail("OPERATION_CONFLICT", "El ID del reembolso ya se utilizó.", 409);
                if (reverseStock) for (let i = 0; i < data.items.length; i++) {
                    if (!products[i].exists) fail("PRODUCT_NOT_FOUND", "Producto histórico inexistente.", 409);
                    const current = products[i].data().stock;
                    const after = current + data.items[i].quantity;
                    if (!Number.isSafeInteger(current) || !Number.isSafeInteger(after) || after > 1000000) fail("INVALID_STOCK", "No se pudo reponer stock.", 409);
                    tx.update(products[i].ref, { stock: after, updatedAt: FieldValue.serverTimestamp() });
                }
                if (needsRefund) {
                    const before = wallet.exists ? wallet.data().balanceCents : 0;
                    const after = before + data.totalCents;
                    if (!Number.isSafeInteger(before) || before < 0 || !Number.isSafeInteger(after)) fail("INVALID_BALANCE", "Saldo inválido.", 409);
                    tx.set(walletRef, { balanceCents: after, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
                    tx.create(refundRef, {
                        type: action === "CANCELLED" ? "STORE_CANCEL_REFUND" : "STORE_REFUND",
                        actorUid: actor, referenceId: orderId, deltaCents: data.totalCents,
                        balanceAfterCents: after, createdAt: FieldValue.serverTimestamp()
                    });
                }
                const patch = { status: action, updatedAt: FieldValue.serverTimestamp() };
                if (action === "DELIVERED") Object.assign(patch, { deliveredAt: FieldValue.serverTimestamp(), deliveredByUid: actor, deliveryNote: note });
                if (action === "READY") Object.assign(patch, { readyAt: FieldValue.serverTimestamp(), readyByUid: actor });
                if (action === "CANCELLED") Object.assign(patch, { cancelledAt: FieldValue.serverTimestamp(), cancelledByUid: actor, cancellationReason: note });
                if (action === "REFUNDED") Object.assign(patch, { refundedAt: FieldValue.serverTimestamp(), refundedByUid: actor, refundReason: note, restocked: false });
                tx.update(orderRef, patch);
                tx.create(eventRef, { action, note, actorUid: actor, statusBefore: previous, statusAfter: action, createdAt: FieldValue.serverTimestamp() });
                return { orderId, status: action, repeated: false };
            });
        },
        async resolveCustomer(actorUid, query) {
            const actor = uid(actorUid);
            if (typeof query !== "string" || query.trim().length < 3 || query.length > 160) fail("INVALID_QUERY", "Indica el correo o UID.");
            const admin = await db.doc(`admin_users/${actor}`).get();
            if (!admin.exists) fail("ADMIN_REQUIRED", "Solo administradores pueden buscar clientes.", 403);
            const value = query.trim();
            let doc;
            if (value.includes("@")) {
                const result = await db.collection("users").where("email", "==", value).limit(1).get();
                doc = result.docs[0];
            } else if (/^[A-Za-z0-9:_-]{6,128}$/.test(value)) {
                doc = await db.doc(`users/${value}`).get();
            }
            if (!doc || !doc.exists) fail("USER_NOT_FOUND", "Cliente no encontrado.", 404);
            return { uid: doc.id, fullName: doc.data().fullName || "", email: doc.data().email || "" };
        }
    };
}
module.exports = { createStoreService, normalizeItems, fingerprint, CATEGORIES };
