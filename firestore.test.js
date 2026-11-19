const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_real_uid_999";
const LEGACY_UID = "yRuPuAgCe2V2Y8avgK9U8n4sTgj1";
const OWNER_EMAIL = "ashrafmahmudsany2@gmail.com";
const EMP_UID = "emp_uid_111";
const EMP_EMAIL = "staff@gmail.com";
const STRANGER_UID = "stranger_uid_222";
const STRANGER_EMAIL = "hacker@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ownerDb(uid = OWNER_UID, emailVerified = true) {
  return testEnv
    .authenticatedContext(uid, {
      email: OWNER_EMAIL,
      email_verified: emailVerified,
    })
    .firestore();
}

function empDb(uid = EMP_UID, email = EMP_EMAIL, emailVerified = true) {
  return testEnv
    .authenticatedContext(uid, {
      email,
      email_verified: emailVerified,
    })
    .firestore();
}

function strangerDb() {
  return testEnv
    .authenticatedContext(STRANGER_UID, {
      email: STRANGER_EMAIL,
      email_verified: true,
    })
    .firestore();
}

async function seedVerifiedOwnerAndAccounts() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = new Date(Date.now() - 5000);
    await db.collection("app_config").doc("owner").set({
      ownerEmail: OWNER_EMAIL,
      ownerUid: OWNER_UID,
      legacySuperAdminUid: LEGACY_UID,
      uidVerified: true,
      updatedAt: now,
    });
    await db.collection("accounts").doc("cash").set({
      name: "Cash",
      category: "CASH",
      lastFourDigits: "",
      balancePaisa: 0,
      sortOrder: 1,
      updatedAt: now,
    });
    await db.collection("accounts").doc("bkash_1").set({
      name: "bKash-1",
      category: "BKASH",
      lastFourDigits: "0198",
      balancePaisa: 0,
      sortOrder: 2,
      updatedAt: now,
    });
    await db.collection("employee_access").doc(EMP_EMAIL).set({
      email: EMP_EMAIL,
      name: "Karim Staff",
      role: "EMPLOYEE",
      active: true,
      linkedUid: EMP_UID,
      addedByUid: OWNER_UID,
      createdAt: now,
      updatedAt: now,
    });
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user cannot read or write any collection", async () => {
  await seedVerifiedOwnerAndAccounts();
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("accounts").get());
  await assertFails(unauth.collection("transactions").get());
  await assertFails(unauth.collection("app_config").doc("owner").get());
});

test("Owner UID verification: mismatched UID or unverified email cannot assume Owner privileges", async () => {
  await seedVerifiedOwnerAndAccounts();
  const now = new Date(Date.now() - 1000);

  // Email spoofing with email_verified=false fails even with owner email
  const spoofedOwner = ownerDb(OWNER_UID, false);
  await assertFails(
    spoofedOwner.collection("accounts").doc("cash").update({
      balancePaisa: 1000000,
      updatedAt: now,
    })
  );

  // Owner email with DIFFERENT UID (not matching app_config/owner.ownerUid) cannot modify accounts!
  const mismatchedUidOwner = ownerDb("wrong_uid_777", true);
  await assertFails(
    mismatchedUidOwner.collection("accounts").doc("cash").update({
      balancePaisa: 1000000,
      updatedAt: now,
    })
  );

  // Stranger cannot write app_config/owner to hijack ownership
  await assertFails(
    strangerDb().collection("app_config").doc("owner").set({
      ownerEmail: OWNER_EMAIL,
      ownerUid: STRANGER_UID,
      legacySuperAdminUid: LEGACY_UID,
      uidVerified: true,
      updatedAt: now,
    })
  );

  // Verified owner email CAN safely bind their own real UID to app_config/owner
  await assertSucceeds(
    mismatchedUidOwner.collection("app_config").doc("owner").set({
      ownerEmail: OWNER_EMAIL,
      ownerUid: "wrong_uid_777",
      legacySuperAdminUid: LEGACY_UID,
      uidVerified: true,
      updatedAt: now,
    })
  );
});

test("Verified Owner can initialize accounts, record transactions, and reverse them", async () => {
  await seedVerifiedOwnerAndAccounts();
  const db = ownerDb();
  const now = new Date(Date.now() - 1000);

  // Balance update on Cash
  await assertSucceeds(
    db.collection("accounts").doc("cash").update({
      balancePaisa: 1000000,
      updatedAt: now,
    })
  );

  // Create approved transaction (NO id field)
  await assertSucceeds(
    db.collection("transactions").doc("tx_1").set({
      type: "BALANCE_ADD",
      subType: "CAPITAL",
      title: "মূলধন যোগ - Cash",
      partyName: "",
      phone: "",
      receiveAccountId: "cash",
      receiveAmountPaisa: 1000000,
      payAccountId: "",
      payAmountPaisa: 0,
      commissionIncomePaisa: 0,
      commissionExpensePaisa: 0,
      expenseAmountPaisa: 0,
      interestIncomePaisa: 0,
      interestExpensePaisa: 0,
      capitalDeltaPaisa: 1000000,
      externalTxId: "",
      idempotencyKey: "req_1",
      note: "Initial capital",
      businessDateMillis: Date.now() - 1000,
      partnerId: "",
      loanId: "",
      operatorName: "",
      submittedByUid: OWNER_UID,
      submittedByName: "Owner",
      approvedByUid: OWNER_UID,
      reversed: false,
      reversedByTxId: "",
      reversalOfTxId: "",
      reversalReason: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject ghost field on transaction
  await assertFails(
    db.collection("transactions").doc("tx_ghost").set({
      id: "tx_ghost",
      type: "BALANCE_ADD",
      subType: "CAPITAL",
      title: "Ghost",
      partyName: "",
      phone: "",
      receiveAccountId: "cash",
      receiveAmountPaisa: 100,
      payAccountId: "",
      payAmountPaisa: 0,
      commissionIncomePaisa: 0,
      commissionExpensePaisa: 0,
      expenseAmountPaisa: 0,
      interestIncomePaisa: 0,
      interestExpensePaisa: 0,
      capitalDeltaPaisa: 100,
      externalTxId: "",
      idempotencyKey: "req_ghost",
      note: "",
      businessDateMillis: Date.now() - 1000,
      partnerId: "",
      loanId: "",
      operatorName: "",
      submittedByUid: OWNER_UID,
      submittedByName: "Owner",
      approvedByUid: OWNER_UID,
      reversed: false,
      reversedByTxId: "",
      reversalOfTxId: "",
      reversalReason: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reversal update succeeds once, fails second time
  await assertSucceeds(
    db.collection("transactions").doc("tx_1").update({
      reversed: true,
      reversedByTxId: "tx_rev_1",
      reversalReason: "ভুল এন্ট্রি",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    db.collection("transactions").doc("tx_1").update({
      reversed: false,
      reversedByTxId: "",
      reversalReason: "",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Employee RBAC: can submit PENDING and read own pending, cannot approve or modify balances/roles", async () => {
  await seedVerifiedOwnerAndAccounts();
  const emp = empDb();
  const now = new Date(Date.now() - 1000);

  // Cannot modify accounts directly
  await assertFails(
    emp.collection("accounts").doc("cash").update({
      balancePaisa: 500000,
      updatedAt: now,
    })
  );

  // Cannot modify own employee_access role or add employees
  await assertFails(
    emp.collection("employee_access").doc(EMP_EMAIL).update({
      name: "Admin",
      updatedAt: now,
    })
  );

  // Cannot submit already-APPROVED pending transaction
  await assertFails(
    emp.collection("pending_transactions").doc("p_bad").set({
      status: "APPROVED",
      type: "CUSTOMER_TX",
      subType: "",
      title: "Customer Tx",
      partyName: "Rahim",
      phone: "",
      receiveAccountId: "bkash_1",
      receiveAmountPaisa: 50000,
      payAccountId: "cash",
      payAmountPaisa: 50000,
      commissionIncomePaisa: 1000,
      commissionExpensePaisa: 0,
      expenseAmountPaisa: 0,
      interestIncomePaisa: 0,
      interestExpensePaisa: 0,
      capitalDeltaPaisa: 0,
      externalTxId: "TRX123",
      idempotencyKey: "k1",
      note: "",
      businessDateMillis: Date.now() - 1000,
      partnerId: "",
      loanId: "",
      operatorName: "",
      submittedByUid: EMP_UID,
      submittedByEmail: EMP_EMAIL,
      submittedByName: "Karim",
      reviewedByUid: "",
      approvedTxId: "",
      rejectionReason: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid PENDING submission succeeds
  await assertSucceeds(
    emp.collection("pending_transactions").doc("p_1").set({
      status: "PENDING",
      type: "CUSTOMER_TX",
      subType: "",
      title: "Customer Tx",
      partyName: "Rahim",
      phone: "",
      receiveAccountId: "bkash_1",
      receiveAmountPaisa: 50000,
      payAccountId: "cash",
      payAmountPaisa: 50000,
      commissionIncomePaisa: 1000,
      commissionExpensePaisa: 0,
      expenseAmountPaisa: 0,
      interestIncomePaisa: 0,
      interestExpensePaisa: 0,
      capitalDeltaPaisa: 0,
      externalTxId: "TRX123",
      idempotencyKey: "k1",
      note: "",
      businessDateMillis: Date.now() - 1000,
      partnerId: "",
      loanId: "",
      operatorName: "",
      submittedByUid: EMP_UID,
      submittedByEmail: EMP_EMAIL,
      submittedByName: "Karim",
      reviewedByUid: "",
      approvedTxId: "",
      rejectionReason: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Employee filtered query succeeds; unfiltered query fails
  await assertSucceeds(
    emp.collection("pending_transactions").where("submittedByUid", "==", EMP_UID).get()
  );
  await assertFails(emp.collection("pending_transactions").get());

  // Employee cannot approve own pending transaction
  await assertFails(
    emp.collection("pending_transactions").doc("p_1").update({
      status: "APPROVED",
      reviewedByUid: EMP_UID,
      approvedTxId: "tx_x",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Owner CAN approve pending transaction
  await assertSucceeds(
    ownerDb().collection("pending_transactions").doc("p_1").update({
      status: "APPROVED",
      reviewedByUid: OWNER_UID,
      approvedTxId: "tx_real_1",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Stranger / Unauthorized user cannot read or write any business data", async () => {
  await seedVerifiedOwnerAndAccounts();
  const s = strangerDb();
  await assertFails(s.collection("accounts").get());
  await assertFails(s.collection("partners").get());
  await assertFails(s.collection("transactions").get());
  await assertFails(s.collection("loans").get());
  await assertFails(s.collection("audit_logs").get());
});
