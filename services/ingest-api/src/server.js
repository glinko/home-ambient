import express from "express";
import { Pool } from "pg";
import { z } from "zod";

const app = express();
app.use(express.json({ limit: "2mb" }));

const pool = new Pool({
  connectionString:
    process.env.DATABASE_URL || "postgres://postgres:postgres@localhost:5432/homeambient",
});

const EventSchema = z.object({
  id: z.string(),
  device_id: z.string(),
  ts: z.string(),
  motion_score: z.number().optional(),
  stt_text: z.string().optional(),
  person_id: z.string().optional(),
  metadata: z.record(z.any()).default({}),
});

app.get("/health", (_req, res) => res.json({ ok: true }));

app.post("/v1/events", async (req, res) => {
  const parsed = EventSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  const e = parsed.data;
  try {
    await pool.query(
      `INSERT INTO events (id, device_id, ts, motion_score, stt_text, person_id, metadata)
       VALUES ($1,$2,$3,$4,$5,$6,$7)
       ON CONFLICT (id) DO NOTHING`,
      [
        e.id,
        e.device_id,
        e.ts,
        e.motion_score ?? null,
        e.stt_text ?? null,
        e.person_id ?? null,
        JSON.stringify(e.metadata),
      ]
    );
    res.status(202).json({ ok: true });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "db_error" });
  }
});

const port = process.env.PORT || 8070;
app.listen(port, () => console.log(`ingest-api listening on :${port}`));
