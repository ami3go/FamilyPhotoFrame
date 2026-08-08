/**
 * The merged-pool rules decide what the frame plays when sources come and go. They were
 * written in an environment with no Android toolchain, so getting them *compiled and run*
 * here is the only real check they have had.
 */
fun runSourcePoolPolicyChecks() {
    println("-- initial plan --")
    run {
        val plan = SourcePoolPolicy.initialPlan(emptyList())
        check("nothing configured", true, plan is SourcePoolPolicy.Plan.NothingConfigured)
    }
    run {
        val plan = SourcePoolPolicy.initialPlan(listOf(
            SourcePoolPolicy.Slot("local_saf", healthy = true, isChosen = true),
            SourcePoolPolicy.Slot("smb", healthy = true),
        ))
        check("both healthy sources play", listOf("local_saf", "smb"),
            (plan as SourcePoolPolicy.Plan.Play).primaryIds)
    }
    run {
        // The whole point of merged pools: one source down must not stop the other.
        val plan = SourcePoolPolicy.initialPlan(listOf(
            SourcePoolPolicy.Slot("local_saf", healthy = true, isChosen = true),
            SourcePoolPolicy.Slot("smb", healthy = false),
        ))
        check("unhealthy co-primary is dropped, not fatal", listOf("local_saf"),
            (plan as SourcePoolPolicy.Plan.Play).primaryIds)
    }
    run {
        val plan = SourcePoolPolicy.initialPlan(listOf(
            SourcePoolPolicy.Slot("smb", healthy = false, isChosen = true),
            SourcePoolPolicy.Slot("webdav", healthy = false),
        ))
        check("all down -> unreachable policy for the chosen source", "smb",
            (plan as SourcePoolPolicy.Plan.Unreachable).sourceId)
    }
    run {
        // No slot flagged chosen: fall back to the first rather than losing the plan.
        val plan = SourcePoolPolicy.initialPlan(listOf(
            SourcePoolPolicy.Slot("webdav", healthy = false),
        ))
        check("defaults chosen to first slot", "webdav",
            (plan as SourcePoolPolicy.Plan.Unreachable).sourceId)
    }

    println("-- pool order is stable --")
    run {
        val ids = listOf("webdav", "local_saf", "smb")
        val plan = SourcePoolPolicy.planFor(ids, "smb")
        check("order preserved, not sorted", ids, (plan as SourcePoolPolicy.Plan.Play).primaryIds)
    }
    run {
        val plan = SourcePoolPolicy.planFor(listOf("smb", "smb"), "smb")
        check("duplicates collapsed", listOf("smb"), (plan as SourcePoolPolicy.Plan.Play).primaryIds)
    }

    println("-- promote / demote --")
    run {
        check("promote appends", listOf("local_saf", "smb"),
            SourcePoolPolicy.afterPromote(listOf("local_saf"), "smb"))
        check("promote is idempotent", listOf("smb"),
            SourcePoolPolicy.afterPromote(listOf("smb"), "smb"))
        check("demote removes only that source", listOf("local_saf"),
            SourcePoolPolicy.afterDemote(listOf("local_saf", "smb"), "smb"))
        check("demote of absent source is a no-op", listOf("local_saf"),
            SourcePoolPolicy.afterDemote(listOf("local_saf"), "smb"))
    }
    run {
        // Regression: a recovering source must rejoin, not replace, the pool.
        val pool = SourcePoolPolicy.afterPromote(listOf("local_saf"), "smb")
        val plan = SourcePoolPolicy.planFor(pool, "smb")
        check("recovered source joins co-primaries", listOf("local_saf", "smb"),
            (plan as SourcePoolPolicy.Plan.Play).primaryIds)
    }
    run {
        val pool = SourcePoolPolicy.afterDemote(listOf("smb"), "smb")
        val plan = SourcePoolPolicy.planFor(pool, "smb")
        check("last source lost -> unreachable", "smb",
            (plan as SourcePoolPolicy.Plan.Unreachable).sourceId)
    }
}
