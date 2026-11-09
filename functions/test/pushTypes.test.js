// Which pushes a user's users/{uid}.pushTypes lets through; plain logic, no emulator needed.
const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const { acceptsPush } = require("../lib/pushTypes");

describe("pushTypes", () => {
  it("lets through only the listed types", () => {
    assert.equal(acceptsPush(["invite"], "invite"), true);
    assert.equal(acceptsPush(["invite"], "request"), false);
    assert.equal(acceptsPush(["invite", "request"], "request"), true);
  });

  it("treats an empty list as every push turned off", () => {
    assert.equal(acceptsPush([], "invite"), false);
    assert.equal(acceptsPush([], "request"), false);
  });

  it("gives a build that doesn't write the list invite pushes only", () => {
    assert.equal(acceptsPush(undefined, "invite"), true);
    assert.equal(acceptsPush(undefined, "request"), false);
  });
});
