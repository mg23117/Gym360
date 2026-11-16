"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { createAccountService, plusDays, salvadorToday } = require("../src/accountService");

const today = salvadorToday(new Date("2026-11-13T18:00:00Z"));
function fakeDb() {
  const store = new Map();
  class Ref {
    constructor(path) { this.path = path; }
    collection(name) { return { doc: id => new Ref(`${this.path}/${name}/${id}`) }; }
  }
  const db = {
    doc(path) { return new Ref(path); },
    async runTransaction(cb) {
      const pending = [];
      let wrote = false;
      const tx = {
        get: async ref => {
          if (wrote) throw Error("read after write");
          return { exists: store.has(ref.path), data: () => structuredClone(store.get(ref.path)) };
        },
        set: (ref, data, opts) => { wrote = true; pending.push(["set",ref,data,opts]); },
        create: (ref, data) => { wrote = true; pending.push(["create",ref,data]); }
      };
      const result = await cb(tx);
      for (const [kind,ref,data,opts] of pending) {
        if (kind === "create" && store.has(ref.path)) throw Error("already exists");
        store.set(ref.path, kind === "set" && opts?.merge ? { ...store.get(ref.path), ...data } : data);
      }
      return result;
    }
  };
  const service = createAccountService(db, { serverTimestamp: () => "SERVER_TIME" }, () => new Date("2026-11-13T18:00:00Z"));
  store.set("admin_users/adminABC", {});
  store.set("users/clientABC", {uid:"clientABC",email:"client@example.com"});
  store.set("memberships/monthly", {id:"monthly",name:"Mensual",durationMonths:1,price:30});
  store.set("memberships/fortnight", {id:"fortnight",name:"Quincenal",durationMonths:0,durationDays:15,price:15});
  store.set("memberships/quarterly", {id:"quarterly",name:"Trimestral",durationMonths:3,price:80});
  return { store, service };
}

test("solo administrador recarga saldo y reintentar no duplica", async () => {
  const {store,service} = fakeDb();
  await assert.rejects(service.adjustWallet("clientABC",{targetUid:"clientABC",deltaCents:5000,reason:"Pago en sucursal",operationId:"cashId0001"}), e => e.status === 403);
  const r = await service.adjustWallet("adminABC",{targetUid:"clientABC",deltaCents:5000,reason:"Pago en sucursal",operationId:"cashId0001"});
  assert.equal(r.balanceCents,5000);
  assert.equal(store.get("wallets/clientABC/transactions/cashId0001").deltaCents,5000);
  const repeated = await service.adjustWallet("adminABC",{targetUid:"clientABC",deltaCents:5000,reason:"Pago en sucursal",operationId:"cashId0001"});
  assert.equal(repeated.repeated, true);
  assert.equal(store.get("wallets/clientABC").balanceCents,5000);
  await assert.rejects(service.adjustWallet("adminABC",{targetUid:"clientABC",deltaCents:9000,reason:"Pago en sucursal",operationId:"cashId0001"}), e=>e.code === "OPERATION_CONFLICT");
});

test("10 + 15 = 25 conserva mensual y descuento correcto", async () => {
  const {store, service} = fakeDb();
  store.set("wallets/clientABC", {balanceCents:20000});
  store.set("user_memberships/clientABC", {uid:"clientABC",activePlanId:"monthly",activePlanName:"Mensual",activePlanWeightDays:30,expiresOnExclusive:plusDays(today,10)});
  const receipt = await service.purchaseMembership("clientABC", {membershipId:"fortnight",operationId:"buyTest001"});
  assert.equal(receipt.balanceCents,18500);
  assert.equal(receipt.expiresOnExclusive,plusDays(today,25));
  assert.equal(receipt.activePlanName,"Mensual");
  const again = await service.purchaseMembership("clientABC",{membershipId:"fortnight",operationId:"buyTest001"});
  assert.equal(again.repeated,true);
  assert.equal(store.get("wallets/clientABC").balanceCents,18500);
});

test("10 + 90 = 100 promociona trimestral", async () => {
  const {store, service} = fakeDb();
  store.set("wallets/clientABC",{balanceCents:20000});
  store.set("user_memberships/clientABC", {uid:"clientABC",activePlanId:"monthly",activePlanName:"Mensual",activePlanWeightDays:30,expiresOnExclusive:plusDays(today,10)});
  const r = await service.purchaseMembership("clientABC",{membershipId:"quarterly",operationId:"buyTest002"});
  assert.equal(r.expiresOnExclusive,plusDays(today,100));
  assert.equal(r.activePlanName,"Trimestral");
  assert.equal(r.balanceCents,12000);
});

test("saldo insuficiente no genera cobro ni compra", async () => {
  const {store, service} = fakeDb();
  store.set("wallets/clientABC",{balanceCents:400});
  await assert.rejects(service.purchaseMembership("clientABC",{membershipId:"fortnight",operationId:"buyTest003"}), e=>e.code === "INSUFFICIENT_BALANCE");
  assert.equal(store.get("wallets/clientABC").balanceCents,400);
  assert.equal(store.has("users/clientABC/membership_purchases/buyTest003"), false);
});

test("no se aceptan importes ni precio inyectado por cliente", async () => {
  const {store, service} = fakeDb();
  store.set("wallets/clientABC",{balanceCents:10000});
  const r = await service.purchaseMembership("clientABC",{membershipId:"monthly",operationId:"buyTest004",price:0});
  assert.equal(r.balanceCents,7000);
  assert.equal(store.get("users/clientABC/membership_purchases/buyTest004").priceCents,3000);
});

test("un mismo operationId con otros datos se rechaza", async () => {
  const {store,service}=fakeDb();
  store.set("wallets/clientABC",{balanceCents:10000});
  await service.purchaseMembership("clientABC",{membershipId:"monthly",operationId:"buyTest005"});
  await assert.rejects(service.purchaseMembership("clientABC",{membershipId:"fortnight",operationId:"buyTest005"}),e=>e.code==="OPERATION_CONFLICT");
});
