/**
 * Must load before any onCall / trigger registration so region applies.
 * Import this module first from every file that defines Cloud Functions.
 */
import { setGlobalOptions } from "firebase-functions/v2";

setGlobalOptions({
  region: "asia-east1",
  maxInstances: 20,
});

/** Shared region for explicit per-function options (belt-and-suspenders). */
export const FUNCTIONS_REGION = "asia-east1" as const;
