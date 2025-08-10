const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

/* ================= 1) LOG ACTIVITY → PUSH לטופיק group_{groupId} ================= */
exports.onActivityCreated = functions
  .region("europe-west1")
  .firestore
  .document("groups/{groupId}/subCompetitions/{subId}/activities/{activityId}")
  .onCreate(async (snap, context) => {
    const data = snap.data() || {};
    const groupId = context.params.groupId;
    const subId = context.params.subId;
    const username = data.username || "Someone";

    try {
      await admin.messaging().send({
        topic: `group_${groupId}`,
        notification: {
          title: "FOMO",
          body: `${username} has just logged an activity!`,
        },
        data: { type: "activity", groupId, subCompetitionId: subId },
      });
      console.log("onActivityCreated → topic:", `group_${groupId}`);
    } catch (e) {
      console.error("onActivityCreated error:", e);
    }
  });

/* ================= 2) נוספה הזמנה → PUSH אל המוזמנים ================= */
exports.onGroupInviteAdded = functions
  .region("europe-west1")
  .firestore
  .document("groups/{groupId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data() || {};
    const after  = change.after.data()  || {};
    const groupId = context.params.groupId;

    const beforeInv = before.pendingInvites || [];
    const afterInv  = after.pendingInvites  || [];
    const newlyInvited = afterInv.filter(uid => !beforeInv.includes(uid));
    if (newlyInvited.length === 0) return null;

    const groupName = after.name || "a group";

    let inviterName = "Someone";
    try {
      const inviterId = after.lastInvitedBy;
      if (inviterId) {
        const inviterDoc = await admin.firestore().collection("users").doc(inviterId).get();
        inviterName = (inviterDoc.exists && inviterDoc.get("username")) || inviterName;
      }
    } catch (_) {}

    const userRefs = newlyInvited.map(uid => admin.firestore().collection("users").doc(uid));
    const userDocs = await admin.firestore().getAll(...userRefs);

    const tokens = [];
    for (const doc of userDocs) {
      if (!doc.exists) continue;
      const arr = doc.get("fcmTokens");
      const single = doc.get("fcmToken");
      if (Array.isArray(arr)) tokens.push(...arr);
      if (single) tokens.push(single);
    }
    if (tokens.length === 0) return null;

    try {
      const res = await admin.messaging().sendEachForMulticast({
        notification: {
          title: "FOMO",
          body: `You have been invited by ${inviterName} to join ${groupName}`,
        },
        data: { type: "invite", groupId },
        tokens,
      });
      console.log("onGroupInviteAdded →", res.successCount, "success");
    } catch (e) {
      console.error("onGroupInviteAdded error:", e);
    }
    return null;
  });

/* ================= 3) סאב-תחרות נסגרה (closed=true) → PUSH עם המנצח ================= */
exports.onSubCompetitionClosed = functions
  .region("europe-west1")
  .firestore
  .document("groups/{groupId}/subCompetitions/{subId}")
  .onUpdate(async (change, ctx) => {
    const before = change.before.data() || {};
    const after  = change.after.data()  || {};
    if (before.closed === true || after.closed !== true) return null;

    const groupId = ctx.params.groupId;
    const subId   = ctx.params.subId;

    const points = after.points || {};
    const entries = Object.entries(points).map(([uid, val]) => [uid, Number(val || 0)]);
    if (entries.length === 0) {
      await admin.messaging().send({
        topic: `group_${groupId}`,
        notification: {
          title: "FOMO",
          body: `“${after.name || "Challenge"}” finished.`,
        },
        data: { type: "sub_closed", groupId: String(groupId), subCompetitionId: String(subId) },
      });
      return null;
    }

    entries.sort((a, b) => b[1] - a[1] || String(a[0]).localeCompare(String(b[0])));
    const [winnerId, winnerCount] = entries[0];

    let winnerName = "Someone";
    try {
      const uDoc = await admin.firestore().collection("users").doc(String(winnerId)).get();
      if (uDoc.exists) winnerName = uDoc.get("username") || winnerName;
    } catch (_) {}

    try {
      await admin.messaging().send({
        topic: `group_${groupId}`,
        notification: {
          title: "FOMO",
          body: `“${after.name || "Challenge"}” finished — ${winnerName} won with ${winnerCount} activities!`,
        },
        data: {
          type: "sub_finished",
          groupId: String(groupId),
          subCompetitionId: String(subId),
          winnerId: String(winnerId),
        },
      });
      console.log("onSubCompetitionClosed → topic:", `group_${groupId}`);
    } catch (e) {
      console.error("onSubCompetitionClosed error:", e);
    }
    return null;
  });

/* ================= 4) תזכורת “יום לפני” לקבוצה/סאב-תחרות ================= */
exports.reminderOneDayBefore = functions
  .region("europe-west1")
  .pubsub.schedule("every 60 minutes")
  .onRun(async () => {
    const db = admin.firestore();
    const now = new Date();
    const target = new Date(now);
    target.setUTCHours(0,0,0,0);
    target.setUTCDate(target.getUTCDate() + 1);

    const yyyy = target.getUTCFullYear();
    const mm   = String(target.getUTCMonth() + 1).padStart(2, "0");
    const dd   = String(target.getUTCDate()).padStart(2, "0");
    const targetStr = `${yyyy}-${mm}-${dd}`;

    const start = new Date(Date.UTC(yyyy, target.getUTCMonth(), target.getUTCDate()));
    const end   = new Date(Date.UTC(yyyy, target.getUTCMonth(), target.getUTCDate() + 1));
    const tsStart = admin.firestore.Timestamp.fromDate(start);
    const tsEnd   = admin.firestore.Timestamp.fromDate(end);

    const groupsSnap = await db.collection("groups")
      .where("endDate", "==", targetStr)
      .get();

    const groupSends = groupsSnap.docs.map(doc => {
      const groupId = doc.id;
      const groupName = doc.get("name") || "Group";
      return admin.messaging().send({
        topic: `group_${groupId}`,
        notification: {
          title: "FOMO",
          body: `${groupName} is about to finish, hurry up and log an activity!`,
        },
        data: { type: "group_last_day", groupId },
      });
    });

    const subsSnap = await db.collectionGroup("subCompetitions")
      .where("endDate", ">=", tsStart)
      .where("endDate", "<", tsEnd)
      .get();

    const subSends = subsSnap.docs
      .filter(d => d.get("closed") !== true)
      .map(doc => {
        const subId = doc.id;
        const groupRef = doc.ref.parent.parent;
        const groupId = groupRef ? groupRef.id : "";
        const name = doc.get("name") || "Challenge";
        return admin.messaging().send({
          topic: `group_${groupId}`,
          notification: {
            title: "FOMO",
            body: `${name} is about to finish, hurry up and log an activity!`,
          },
          data: { type: "sub_last_day", groupId, subCompetitionId: subId },
        });
      });

    await Promise.all([...groupSends, ...subSends]);
    console.log("reminderOneDayBefore done");
    return null;
  });

/* ================= 5) קבוצה הסתיימה → יוצרים גביע + PUSH (מתוזמן) ================= */
exports.closeGroupsAndAwardTrophy = functions
  .region("europe-west1")
  .pubsub.schedule("every 60 minutes")
  .onRun(async () => {
    const db = admin.firestore();

    const now = new Date();
    now.setUTCHours(0,0,0,0);
    const yyyy = now.getUTCFullYear();
    const mm   = String(now.getUTCMonth() + 1).padStart(2, "0");
    const dd   = String(now.getUTCDate()).padStart(2, "0");
    const todayStr = `${yyyy}-${mm}-${dd}`;

    const snap = await db.collection("groups")
      .where("endDate", "<=", todayStr)
      .get();

    const tasks = snap.docs.map(async (doc) => {
      const g = doc.data() || {};
      if (g.closed === true) return;

      const groupId = doc.id;
      const groupName = g.name || "Group";

      const trophyId = `group_${groupId}`;
      const trophyDoc = await db.collection("trophies").doc(trophyId).get();
      if (trophyDoc.exists) {
        await doc.ref.update({ closed: true });
        return;
      }

      const leaderboard = g.leaderboard || {};
      const entries = Object.entries(leaderboard).map(([uid, val]) => [uid, Number(val || 0)]);
      if (entries.length === 0) {
        await doc.ref.update({ closed: true });
        return;
      }
      entries.sort((a, b) => b[1] - a[1] || String(a[0]).localeCompare(String(b[0])));
      const [winnerId, wins] = entries[0];

      let winnerName = "Someone";
      try {
        const uDoc = await db.collection("users").doc(String(winnerId)).get();
        if (uDoc.exists) winnerName = uDoc.get("username") || winnerName;
      } catch (_) {}

      await db.runTransaction(async (tx) => {
        tx.set(db.collection("trophies").doc(trophyId), {
          type: "group",
          groupId,
          groupName,
          endDate: g.endDate || todayStr,
          winnerId: String(winnerId),
          winnerName,
          wins: wins,
        }, { merge: true });
        tx.update(doc.ref, { closed: true });
      });

      await admin.messaging().send({
        topic: `group_${groupId}`,
        notification: {
          title: "FOMO",
          body: `${winnerName} won the group "${groupName}" with ${wins} wins — trophy awarded!`,
        },
        data: {
          type: "group_winner",
          groupId: String(groupId),
          winnerId: String(winnerId),
        },
      });

      try {
        const uDoc = await db.collection("users").doc(String(winnerId)).get();
        const tokens = [];
        const arr = uDoc.get("fcmTokens");
        const single = uDoc.get("fcmToken");
        if (Array.isArray(arr)) tokens.push(...arr);
        if (single) tokens.push(single);
        if (tokens.length) {
          await admin.messaging().sendEachForMulticast({
            notification: {
              title: "🏆 You won the group!",
              body: `You won "${groupName}" with ${wins} wins.`,
            },
            data: { type: "group_trophy", groupId: String(groupId) },
            tokens,
          });
        }
      } catch (e) {
        console.warn("winner direct notify failed:", e?.message);
      }
    });

    await Promise.all(tasks);
    console.log("closeGroupsAndAwardTrophy done");
    return null;
  });

/* ================= 6) חדש: קבוצה נסגרת (closed=true) → גביע + PUSH בזמן אמת ================= */
exports.onGroupClosed = functions
  .region("europe-west1")
  .firestore
  .document("groups/{groupId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data() || {};
    const after  = change.after.data()  || {};
    const groupId = context.params.groupId;

    if (before.closed === true || after.closed !== true) return null;

    const db = admin.firestore();
    const groupName = after.name || "Group";

    const trophyId = `group_${groupId}`;
    const trophyDoc = await db.collection("trophies").doc(trophyId).get();
    if (trophyDoc.exists) return null;

    const leaderboard = after.leaderboard || {};
    const entries = Object.entries(leaderboard).map(([uid, val]) => [uid, Number(val || 0)]);
    if (entries.length === 0) return null;

    entries.sort((a, b) => b[1] - a[1] || String(a[0]).localeCompare(String(b[0])));
    const [winnerId, wins] = entries[0];

    let winnerName = "Someone";
    try {
      const uDoc = await db.collection("users").doc(String(winnerId)).get();
      if (uDoc.exists) winnerName = uDoc.get("username") || winnerName;
    } catch (_) {}

    await db.collection("trophies").doc(trophyId).set({
      type: "group",
      groupId,
      groupName,
      endDate: after.endDate || new Date().toISOString().slice(0, 10),
      winnerId: String(winnerId),
      winnerName,
      wins: wins
    }, { merge: true });

    try {
      await admin.messaging().send({
        topic: `group_${groupId}`,
        notification: {
          title: "FOMO",
          body: `${winnerName} won the group "${groupName}" with ${wins} wins — trophy awarded!`,
        },
        data: {
          type: "group_winner",
          groupId: String(groupId),
          winnerId: String(winnerId),
        }
      });
    } catch (e) {
      console.error("Push send error:", e);
    }

    try {
      const uDoc = await db.collection("users").doc(String(winnerId)).get();
      const tokens = [];
      const arr = uDoc.get("fcmTokens");
      const single = uDoc.get("fcmToken");
      if (Array.isArray(arr)) tokens.push(...arr);
      if (single) tokens.push(single);
      if (tokens.length) {
        await admin.messaging().sendEachForMulticast({
          notification: {
            title: "🏆 You won the group!",
            body: `You won "${groupName}" with ${wins} wins.`,
          },
          data: { type: "group_trophy", groupId: String(groupId) },
          tokens,
        });
      }
    } catch (e) {
      console.warn("Winner direct notify failed:", e?.message);
    }

    console.log(`onGroupClosed → Trophy awarded for group ${groupId}`);
    return null;
  });
