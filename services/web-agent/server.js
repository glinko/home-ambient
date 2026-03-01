const express = require("express");
const path = require("path");

const app = express();
app.use(express.static(path.join(__dirname, "public")));

app.get("/health", (_req, res) => res.json({ ok: true, service: "web-agent" }));
app.get("/", (_req, res) => res.sendFile(path.join(__dirname, "public", "agent.html")));

const port = process.env.PORT || 8088;
app.listen(port, "0.0.0.0", () => {
  console.log("web-agent listening on :" + port);
});
