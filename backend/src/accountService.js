"use strict";

const ZONE = "America/El_Salvador";

class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function bad(field) { 
  throw new ApiError(400, "INVALID_ARGUMENT", `${field} inválido.`); 
}

function textId(value, field) {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]{8,80}$/.test(value)) bad(field);
  return value;
}

function userUid(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9:_-]{6,128}$/.test(value)) bad("UID cliente");
  return value;
}

function membershipId(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(value)) bad("Membresía");
  return value;
}

function balance(value) {
  if (!Number.isSafeInteger(value) || value < 0 || value > 9_000_000_000_000) {
    throw new ApiError(409, "INVALID_BALANCE", "Saldo remoto inconsistente.");
  }
  return value;
}

function priceToCents(value) {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0 || value > 500_000) {
    throw new ApiError(409, "INVALID_PRICE", "Precio del plan inválido.");
  }
  const cents = Math.round(value * 100);
  if (Math.abs(value * 100 - cents) > 0.000001 || !Number.isSafeInteger(cents)) {
    throw new ApiError(409, "INVALID_PRICE", "El precio debe tener como máximo 2 decimales.");
  }
  return cents;
}

function planDuration(data) {
  const months = data?.durationMonths;
  const days = data?.durationDays ?? 0;

  if (Number.isInteger(months) && months > 0 && months <= 120 && days === 0) {
    return { grantedDays: months * 30, durationUnit: "MONTHS", durationValue: months };
  }

  if (months === 0 && (days === 1 || days === 15)) {
    return { grantedDays: days, durationUnit: "DAYS", durationValue: days };
  }

  throw new ApiError(409, "INVALID_DURATION", "Duración de membresía inválida.");
}

function salvadorToday(now = new Date()) {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: ZONE, year: "numeric", month: "2-digit", day: "2-digit"
  }).formatToParts(now);

  const part = name => parts.find(p => p.type === name).value;
  
  return `${part("year")}-${part("month")}-${part("day")}`;
}
function plusDays(isoDate, days) {
  const t = Date.parse(`${isoDate}T00:00:00.000Z`);

  if (!Number.isFinite(t) || !Number.isInteger(days) || days < 0 || days > 100_000) {
    throw new ApiError(409, "INVALID_DATE", "Fecha de vencimiento inválida.");
  }

  return new Date(t + days * 86_400_000).toISOString().slice(0, 10);
}

function daysBetween(a, b) {
  const n = (Date.parse(`${b}T00:00:00.000Z`) - Date.parse(`${a}T00:00:00.000Z`)) / 86_400_000;

  return Number.isFinite(n) ? Math.round(n) : 0;
}

function createAccountService(db, FieldValue, now = () => new Date()) {
  return {
    async adjustWallet(actorUid, body) {
      const actor = userUid(actorUid);
      const targetUid = userUid(body?.targetUid);
      const operationId = textId(body?.operationId, "operationId");
      const deltaCents = body?.deltaCents;
      const reason = body?.reason;

      if (!Number.isSafeInteger(deltaCents) || deltaCents === 0 || Math.abs(deltaCents) > 500_000) {
        throw new ApiError(400, "INVALID_ARGUMENT", "El ajuste debe estar entre -500000 y 500000 centavos, sin ser cero.");
      }

      if (typeof reason !== "string" || reason.trim().length < 3 || reason.length > 120) {
        throw new ApiError(400, "INVALID_ARGUMENT", "Indica un motivo de 3 a 120 caracteres.");
      }

      const cleanReason = reason.trim();
      const adminRef = db.doc(`admin_users/${actor}`);
      const userRef = db.doc(`users/${targetUid}`);
      const walletRef = db.doc(`wallets/${targetUid}`);
      const movementRef = walletRef.collection("transactions").doc(operationId);

      return db.runTransaction(async tx => {
        // Firestore requiere completar las lecturas antes de las escrituras.
        const [admin, user, wallet, movement] = await Promise.all([
          tx.get(adminRef), tx.get(userRef), tx.get(walletRef), tx.get(movementRef)
        ]);

        if (!admin.exists) throw new ApiError(403, "ADMIN_REQUIRED", "Solo administradores pueden ajustar saldo.");
        if (!user.exists || user.data()?.uid !== targetUid) {
          throw new ApiError(404, "USER_NOT_FOUND", "No existe el perfil del cliente.");
        }

        if (movement.exists) {
          const previous = movement.data();
          if (previous.actorUid !== actor || previous.type !== "ADMIN_ADJUSTMENT" ||
              previous.deltaCents !== deltaCents || previous.reason !== cleanReason) {
            throw new ApiError(409, "OPERATION_CONFLICT", "Identificador utilizado en otra operación.");
          }
          
          return { balanceCents: previous.balanceAfterCents, repeated: true };
        }

        const current = balance(wallet.exists ? wallet.data()?.balanceCents : 0);
        const updated = current + deltaCents;
        if (updated < 0) throw new ApiError(409, "INSUFFICIENT_BALANCE", "Saldo insuficiente para el descuento.");
        balance(updated);
        tx.set(walletRef, { balanceCents: updated, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        tx.create(movementRef, {
          type: "ADMIN_ADJUSTMENT", actorUid: actor, deltaCents, reason: cleanReason,
          balanceAfterCents: updated, createdAt: FieldValue.serverTimestamp()
        });
        return { balanceCents: updated, repeated: false };
      });
    },

    async purchaseMembership(actorUid, body) {
      const uid = userUid(actorUid);
      const planId = membershipId(body?.membershipId);
      const operationId = textId(body?.operationId, "operationId");
      const profileRef = db.doc(`users/${uid}`);
      const walletRef = db.doc(`wallets/${uid}`);
      const planRef = db.doc(`memberships/${planId}`);
      const summaryRef = db.doc(`user_memberships/${uid}`);
      const purchaseRef = db.doc(`users/${uid}/membership_purchases/${operationId}`);
      const movementRef = walletRef.collection("transactions").doc(operationId);

      return db.runTransaction(async tx => {
        const [profile, wallet, plan, summary, purchase, movement] = await Promise.all([
          tx.get(profileRef), tx.get(walletRef), tx.get(planRef),
          tx.get(summaryRef), tx.get(purchaseRef), tx.get(movementRef)
        ]);
        if (!profile.exists || profile.data()?.uid !== uid) {
          throw new ApiError(409, "PROFILE_REQUIRED", "Perfil del cliente inexistente.");
        }
        // Una compra repetida nunca vuelve a cobrar.
        if (purchase.exists) {
          const previous = purchase.data();
          if (previous.membershipId !== planId || previous.uid !== uid) {
            throw new ApiError(409, "OPERATION_CONFLICT", "Identificador utilizado en otra compra.");
          }
          return {
            purchaseId: operationId,
            balanceCents: previous.balanceAfterCents,
            expiresOnExclusive: previous.expiresOnExclusiveAfter,
            activePlanName: previous.activePlanNameAfter,
            repeated: true
          };
        }

        if (movement.exists) {
          throw new ApiError(409, "OPERATION_CONFLICT", "Identificador utilizado en otro movimiento.");
        }

        if (!plan.exists) throw new ApiError(404, "PLAN_NOT_FOUND", "El plan ya no está disponible.");
        const data = plan.data();

        if (data?.id !== planId || typeof data?.name !== "string" || !data.name.trim()) {
          throw new ApiError(409, "INVALID_PLAN", "Datos del plan inconsistentes.");
        }

        const { grantedDays, durationUnit, durationValue } = planDuration(data);
        const costCents = priceToCents(data.price); // no nos vamoa a confiar en un precio enviado por Android
        const available = balance(wallet.exists ? wallet.data()?.balanceCents : 0);
        if (available < costCents) {
          throw new ApiError(409, "INSUFFICIENT_BALANCE", "Saldo prepago insuficiente.");
        }
        const today = salvadorToday(now());
        const old = summary.exists ? summary.data() : null;
        const oldExpiry = old?.expiresOnExclusive;
        const stillActive = typeof oldExpiry === "string" && /^\d{4}-\d{2}-\d{2}$/.test(oldExpiry)
          && daysBetween(today, oldExpiry) > 0;
        const baseDate = stillActive ? oldExpiry : today;
        const newExpiry = plusDays(baseDate, grantedDays);
        const oldWeight = stillActive && Number.isInteger(old?.activePlanWeightDays)
          ? old.activePlanWeightDays : 0;
        const promotes = !stillActive || grantedDays > oldWeight;
        const activePlanId = promotes ? planId : old.activePlanId;
        const activePlanName = promotes ? data.name : old.activePlanName;
        const activePlanWeightDays = promotes ? grantedDays : oldWeight;
        const after = available - costCents;

        tx.set(walletRef, { balanceCents: after, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        tx.set(summaryRef, {
          uid, activePlanId, activePlanName, activePlanWeightDays,
          expiresOnExclusive: newExpiry, updatedAt: FieldValue.serverTimestamp()
        });
        tx.create(purchaseRef, {
          uid, membershipId: planId, membershipName: data.name,
          durationUnit, durationValue, daysGranted: grantedDays,
          priceCents: costCents, balanceAfterCents: after,
          expiresOnExclusiveAfter: newExpiry, activePlanNameAfter: activePlanName,
          purchasedAt: FieldValue.serverTimestamp()
        });
        tx.create(movementRef, {
          type: "MEMBERSHIP_PURCHASE", actorUid: uid, deltaCents: -costCents,
          referenceId: operationId, membershipId: planId,
          balanceAfterCents: after, createdAt: FieldValue.serverTimestamp()
        });
        return {
          purchaseId: operationId, balanceCents: after,
          expiresOnExclusive: newExpiry, activePlanName,
          grantedDays, remainingDays: daysBetween(today, newExpiry), repeated: false
        };
      });
    }
  };
}

module.exports = { ApiError, createAccountService, salvadorToday, plusDays, daysBetween };

