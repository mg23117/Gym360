"use strict";

const express = require("express");
const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { ApiError, createAccountService } = require("./accountService");


initializeApp({ credential: applicationDefault() });
const account = createAccountService(getFirestore(), FieldValue);
const app = express();
app.disable("x-powered-by");
app.use(express.json({ limit: "16kb" }));
app.use("/api", (req, res, next) => { res.set("Cache-Control", "no-store"); next(); });

app.get("/health", (req, res) => res.status(200).json({ status: "ok", service: "gym360-api" }));

app.use("/api", async (req, res, next) => {
  try {
    const value = req.get("Authorization") || "";
    const match = /^Bearer ([^\s]+)$/.exec(value);
    if (!match) throw new ApiError(401, "UNAUTHENTICATED", "Inicia sesión para continuar.");
    const decoded = await getAuth().verifyIdToken(match[1]);
    if (!decoded.uid) throw new ApiError(401, "UNAUTHENTICATED", "Token inválido.");
    req.authUid = decoded.uid; // nunca confiar en un UID enviado en el cuerpo
    next();
  } catch (error) {
    if (error instanceof ApiError) return next(error);
    next(new ApiError(401, "UNAUTHENTICATED", "Sesión inválida o expirada."));
  }
});

const wrap = fn => (req, res, next) => Promise.resolve(fn(req, res)).catch(next);

app.post("/api/wallet/adjust", wrap(async (req, res) => {
  const result = await account.adjustWallet(req.authUid, req.body);
  res.status(200).json(result);
}));

app.post("/api/memberships/purchase", wrap(async (req, res) => {
  const result = await account.purchaseMembership(req.authUid, req.body);
  res.status(200).json(result);
}));

app.use((req, res) => res.status(404).json({ code: "NOT_FOUND", error: "Ruta no encontrada." }));

app.use((error, req, res, next) => {
  if (error instanceof ApiError) {
    return res.status(error.status).json({ code: error.code, error: error.message });
  }
  if (error instanceof SyntaxError && error.status === 400 && "body" in error) {
    return res.status(400).json({ code: "INVALID_JSON", error: "JSON inválido." });
  }
  if (error.status === 413) {
    return res.status(413).json({ code: "PAYLOAD_TOO_LARGE", error: "Solicitud demasiado grande." });
  }
  // Evitar devolver trazas, tokens o información del servidor al cliente.
  console.error("Gym360 API error:", error.code || error.name || "unknown");
  res.status(503).json({ code: "SERVER_ERROR", error: "No se pudo completar la operación. Reintenta con el mismo ID." });
});

const port = Number(process.env.PORT || 3000);
app.listen(port, "0.0.0.0", () => {
  console.log(`Gym360 API REST escuchando en puerto ${port}`);
});
