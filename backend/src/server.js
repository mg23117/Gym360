"use strict";

const express = require("express");
const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { ApiError, createAccountService } = require("./accountService");
const { createStoreService } = require("./storeService");
const multer = require("multer");
const cloudinary = require("cloudinary").v2;


initializeApp({ credential: applicationDefault() });
const account = createAccountService(getFirestore(), FieldValue);
const store = createStoreService(getFirestore(), FieldValue);
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

// ===== CUENTAS Y MEMBRESÍAS =====
app.post("/api/wallet/adjust", wrap(async (req, res) => {
  const result = await account.adjustWallet(req.authUid, req.body);
  res.status(200).json(result);
}));

// ===== TIENDA, INVENTARIO Y PEDIDOS =====
app.post("/api/memberships/purchase", wrap(async (req, res) => {
  const result = await account.purchaseMembership(req.authUid, req.body);
  res.status(200).json(result);
}));

// ===== TIENDA, INVENTARIO Y PEDIDOS =====
app.post("/api/orders/checkout", wrap(async (req, res) => {
  res.status(200).json(await store.checkout(req.authUid, req.body));
}));
app.post("/api/orders/counter-sale", wrap(async (req, res) => {
  res.status(200).json(await store.counterSale(req.authUid, req.body));
}));
app.post("/api/inventory/adjust", wrap(async (req, res) => {
  res.status(200).json(await store.stockAdjust(req.authUid, req.body));
}));
app.post("/api/orders/:id/transition", wrap(async (req, res) => {
  res.status(200).json(await store.transition(req.authUid, req.params.id, req.body));
}));
app.get("/api/customers/resolve", wrap(async (req, res) => {
  res.status(200).json(await store.resolveCustomer(req.authUid, req.query.q));
}));
app.post("/api/customers/resolve", wrap(async (req, res) => {
  res.status(200).json(await store.resolveCustomer(req.authUid, req.body?.q));
}));

// ===== CARGA DE IMÁGENES =====
const imageUpload = multer({
  storage: multer.memoryStorage(),
  limits: {fileSize: 5 * 1024 * 1024, files: 1},
  fileFilter: (req, file, cb) => {
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.mimetype)) {
      return cb(new ApiError(400, "INVALID_IMAGE", "Solo imágenes JPEG, PNG o WebP."));
    }
    cb(null, true);
  }
}).single("image");
app.post("/api/products/image", (req, res, next) => imageUpload(req, res, next), wrap(async (req, res) => {
  const admin = await getFirestore().doc(`admin_users/${req.authUid}`).get();
  if (!admin.exists) throw new ApiError(403, "ADMIN_REQUIRED", "Solo administradores pueden cargar imágenes.");
  const bytes = req.file?.buffer;
  if (!bytes) throw new ApiError(400, "INVALID_IMAGE", "Selecciona una imagen.");
  const jpeg = bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  const png = bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]));
  const webp = bytes.toString("ascii",0,4) === "RIFF" && bytes.toString("ascii",8,12) === "WEBP";
  if (!(jpeg || png || webp)) throw new ApiError(400, "INVALID_IMAGE", "El archivo no es una imagen válida.");
  if (!process.env.CLOUDINARY_CLOUD_NAME || !process.env.CLOUDINARY_API_KEY || !process.env.CLOUDINARY_API_SECRET) {
    throw new ApiError(503, "IMAGE_STORAGE_DISABLED", "Configura Cloudinary en Render para subir fotografías.");
  }
  cloudinary.config({cloud_name:process.env.CLOUDINARY_CLOUD_NAME,
    api_key:process.env.CLOUDINARY_API_KEY, api_secret:process.env.CLOUDINARY_API_SECRET, secure:true});
  const result = await new Promise((resolve, reject) => {
    const stream = cloudinary.uploader.upload_stream({folder:"gym360/products", resource_type:"image",
      overwrite:false, allowed_formats:["jpg","jpeg","png","webp"]}, (error, upload) => error ? reject(error) : resolve(upload));
    stream.end(bytes);
  });
  res.status(201).json({imageUrl:result.secure_url});
}));

app.use((req, res) => res.status(404).json({ code: "NOT_FOUND", error: "Ruta no encontrada." }));

app.use((error, req, res, next) => {
  if (error instanceof multer.MulterError) {
    return res.status(400).json({code:"UPLOAD_ERROR",error:"Imagen demasiado grande o solicitud inválida."});
  }
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
