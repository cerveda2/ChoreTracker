const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const path = require("node:path");
const { buildRequestMessage } = require("../lib/requestNotification");

const input = {
  token: "recipient-token",
  householdId: "home",
  senderName: "Šárka",
  choreNames: "Nádobí, Vynést koš",
};

describe("request FCM payload contract", () => {
  it("uses the request channel and localized title/body, with the note as the second body argument", () => {
    assert.deepEqual(buildRequestMessage({ ...input, note: "Prosím dnes 🙏" }), {
      token: "recipient-token",
      data: { type: "request", householdId: "home" },
      android: {
        notification: {
          channelId: "chore_requests",
          titleLocKey: "chore_request_notification_title",
          titleLocArgs: ["Šárka"],
          bodyLocKey: "chore_request_notification_body_note",
          bodyLocArgs: ["Nádobí, Vynést koš", "Prosím dnes 🙏"],
        },
      },
    });
  });

  it("uses the body without a note for both absent and empty notes", () => {
    for (const note of [undefined, ""]) {
      const notification = buildRequestMessage({ ...input, note }).android.notification;
      assert.equal(notification.bodyLocKey, "chore_request_notification_body");
      assert.deepEqual(notification.bodyLocArgs, ["Nádobí, Vynést koš"]);
      assert.equal(notification.title, undefined);
      assert.equal(notification.body, undefined);
    }
  });

  it("matches the localization keys and argument counts shipped by both Android locales", () => {
    for (const locale of ["values", "values-cs"]) {
      const xml = readFileSync(path.resolve(__dirname,
        `../../core/notifications/src/main/res/${locale}/strings.xml`), "utf8");
      for (const note of [undefined, "Prosím"]) {
        const notification = buildRequestMessage({ ...input, note }).android.notification;
        for (const prefix of ["title", "body"]) {
          const key = notification[`${prefix}LocKey`];
          const template = xml.match(new RegExp(`<string name="${key}">([^<]*)</string>`));
          assert.ok(template, `${locale} is missing ${key}`);
          const positions = [...template[1].matchAll(/%(\d+)\$s/g)].map((match) => Number(match[1]));
          assert.equal(Math.max(0, ...positions), notification[`${prefix}LocArgs`].length,
            `${locale}/${key} argument count`);
        }
      }
    }
  });
});
