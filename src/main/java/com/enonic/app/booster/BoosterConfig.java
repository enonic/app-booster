package com.enonic.app.booster;

public @interface BoosterConfig
{
    long cacheTtl() default 3_600;

    int cacheSize() default 10_000;

    String appsForceInvalidateOnInstall() default "";

    // Sourced from https://github.com/mpchadwick/tracking-query-params-registry (commit 6c30b8e, fetched 2026-04-20) plus 5 HubSpot params (_hsenc, __hssc, __hstc, __hsfp, hsCtaTracking) absent from the registry.
    String excludeQueryParamsPreset() default "ScCid, __hsfp, __hssc, __hstc, _branch_match_id, _bta_c, _bta_tid, _ga, _gl, _hsenc, _ke, _kx, adgroupid, adid, adtype, bg_aid_k, bg_aid_v, bg_campaign, bg_kw, bg_source, bg_source_id, campid, channable, cid, cq_cmp, cq_con, cq_med, cq_net, cq_plac, cq_plt, cq_src, cq_term, customid, dclid, dm_i, ef_id, epik, fbadid, fbc_id, fbclid, gPromoCode, gQT, gad_campaignid, gad_source, gadid, gbraid, gclid, gclsrc, gdffi, gdfms, gdftrk, h_ad_id, hsCtaTracking, hsa_acc, hsa_ad, hsa_cam, hsa_grp, hsa_kw, hsa_mt, hsa_net, hsa_src, hsa_tgt, hsa_ver, igshid, irclickid, kb, klar_adid, klar_cpid, klar_source, matomo_campaign, matomo_cid, matomo_content, matomo_group, matomo_keyword, matomo_medium, matomo_placement, matomo_source, mc_cid, mc_eid, meta_placement, meta_site_source, mkcid, mkevt, mkrid, mkwid, msclkid, mtm_campaign, mtm_cid, mtm_content, mtm_group, mtm_keyword, mtm_medium, mtm_placement, mtm_source, nb_adtype, nb_ap, nb_expid_meta, nb_fii, nb_klid, nb_kwd, nb_li_ms, nb_lp_ms, nb_mi, nb_mt, nb_pc, nb_pi, nb_placement, nb_ppi, nb_ti, nbt, ndclid, padid, pcrid, piwik_campaign, piwik_keyword, piwik_kwd, pk_campaign, pk_cid, pk_content, pk_keyword, pk_kwd, pk_medium, pk_source, pl_gc, pp, redirect_log_mongo_id, redirect_mongo_id, rtid, s_kwcid, sb_referer_host, scadid, si, sid, sms_click, sms_source, sms_uph, srsltid, toolid, trk_contact, trk_module, trk_msg, trk_sid, ttadid, ttclid, tw_adid, tw_campaign, tw_content, tw_kwdid, tw_source, tw_term, twclid, utm_campaign, utm_content, utm_creative_format, utm_id, utm_klaviyo_id, utm_marketing_tactic, utm_medium, utm_source, utm_source_platform, utm_term, vmcid, wbraid, yclid";

    String excludeQueryParams() default "";

    boolean disableCacheStatusHeader() default false;

    String overrideHeaders() default "";

    String cacheMimeTypes() default "text/html, text/xhtml";
}
