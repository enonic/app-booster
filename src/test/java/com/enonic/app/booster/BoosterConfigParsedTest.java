package com.enonic.app.booster;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoosterConfigParsedTest
{
    @Test
    void defaults()
    {
        BoosterConfig config = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        final BoosterConfigParsed parse = BoosterConfigParsed.parse( config );
        assertEquals( 3600, parse.cacheTtlSeconds() );
        assertEquals(
            Set.of( "ScCid", "__hsfp", "__hssc", "__hstc", "_branch_match_id", "_bta_c", "_bta_tid", "_ga", "_gl", "_hsenc", "_ke", "_kx",
                    "adgroupid", "adid", "adtype", "bg_aid_k", "bg_aid_v", "bg_campaign", "bg_kw", "bg_source", "bg_source_id",
                    "campid", "channable", "cid", "cq_cmp", "cq_con", "cq_med", "cq_net", "cq_plac", "cq_plt", "cq_src", "cq_term",
                    "customid", "dclid", "dm_i", "ef_id", "epik", "fbadid", "fbc_id", "fbclid", "gPromoCode", "gQT",
                    "gad_campaignid", "gad_source", "gadid", "gbraid", "gclid", "gclsrc", "gdffi", "gdfms", "gdftrk",
                    "h_ad_id", "hsCtaTracking", "hsa_acc", "hsa_ad", "hsa_cam", "hsa_grp", "hsa_kw", "hsa_mt", "hsa_net", "hsa_src",
                    "hsa_tgt", "hsa_ver", "igshid", "irclickid", "kb", "klar_adid", "klar_cpid", "klar_source",
                    "matomo_campaign", "matomo_cid", "matomo_content", "matomo_group", "matomo_keyword", "matomo_medium",
                    "matomo_placement", "matomo_source", "mc_cid", "mc_eid", "meta_placement", "meta_site_source",
                    "mkcid", "mkevt", "mkrid", "mkwid", "msclkid", "mtm_campaign", "mtm_cid", "mtm_content", "mtm_group",
                    "mtm_keyword", "mtm_medium", "mtm_placement", "mtm_source", "nb_adtype", "nb_ap", "nb_expid_meta", "nb_fii",
                    "nb_klid", "nb_kwd", "nb_li_ms", "nb_lp_ms", "nb_mi", "nb_mt", "nb_pc", "nb_pi", "nb_placement", "nb_ppi",
                    "nb_ti", "nbt", "ndclid", "padid", "pcrid", "piwik_campaign", "piwik_keyword", "piwik_kwd",
                    "pk_campaign", "pk_cid", "pk_content", "pk_keyword", "pk_kwd", "pk_medium", "pk_source", "pl_gc", "pp",
                    "redirect_log_mongo_id", "redirect_mongo_id", "rtid", "s_kwcid", "sb_referer_host", "scadid", "si", "sid",
                    "sms_click", "sms_source", "sms_uph", "srsltid", "toolid", "trk_contact", "trk_module", "trk_msg", "trk_sid",
                    "ttadid", "ttclid", "tw_adid", "tw_campaign", "tw_content", "tw_kwdid", "tw_source", "tw_term", "twclid",
                    "utm_campaign", "utm_content", "utm_creative_format", "utm_id", "utm_klaviyo_id", "utm_marketing_tactic",
                    "utm_medium", "utm_source", "utm_source_platform", "utm_term", "vmcid", "wbraid", "yclid" ),
            parse.excludeQueryParams() );
        assertEquals( Set.of(), parse.appsForceInvalidateOnInstall() );
        assertEquals( 10000, parse.cacheSize() );
        assertFalse( parse.disableCacheStatusHeader() );
        assertEquals( Map.of(), parse.overrideHeaders() );
        assertEquals( Set.of( "text/html", "text/xhtml" ), parse.cacheMimeTypes() );
    }

    @Test
    void parse()
    {
        BoosterConfig config = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( config.excludeQueryParamsPreset() ).thenReturn( "b, a" );
        when( config.appsForceInvalidateOnInstall() ).thenReturn( "app2, app1" );
        when( config.cacheTtl() ).thenReturn( 86400L );
        when( config.cacheMimeTypes() ).thenReturn( "text/html, text/xhtml, application/json" );
        when( config.overrideHeaders() ).thenReturn( "\"Cache-Control: private, no-store\", \"X-Instance: \"\"jupiter\"\"\"" );
        final BoosterConfigParsed parse = BoosterConfigParsed.parse( config );
        assertEquals( Set.of( "a", "b" ), parse.excludeQueryParams() );
        assertEquals( Set.of( "app1", "app2" ), parse.appsForceInvalidateOnInstall() );
        assertEquals( 86400L, parse.cacheTtlSeconds() );
        assertEquals( Map.of( "Cache-Control", "private, no-store", "X-Instance", "\"jupiter\"" ), parse.overrideHeaders() );
        assertEquals( Set.of( "text/html", "text/xhtml", "application/json" ), parse.cacheMimeTypes() );
    }

    @Test
    void excludeQueryParamsPreset()
    {
        BoosterConfig config = mock( BoosterConfig.class, invocation -> invocation.getMethod().getDefaultValue() );
        when( config.excludeQueryParams() ).thenReturn( "c, d" );
        final BoosterConfigParsed parse = BoosterConfigParsed.parse( config );
        assertEquals(
            Set.of( "c", "d", "ScCid", "__hsfp", "__hssc", "__hstc", "_branch_match_id", "_bta_c", "_bta_tid", "_ga", "_gl", "_hsenc",
                    "_ke", "_kx", "adgroupid", "adid", "adtype", "bg_aid_k", "bg_aid_v", "bg_campaign", "bg_kw", "bg_source",
                    "bg_source_id", "campid", "channable", "cid", "cq_cmp", "cq_con", "cq_med", "cq_net", "cq_plac", "cq_plt",
                    "cq_src", "cq_term", "customid", "dclid", "dm_i", "ef_id", "epik", "fbadid", "fbc_id", "fbclid",
                    "gPromoCode", "gQT", "gad_campaignid", "gad_source", "gadid", "gbraid", "gclid", "gclsrc", "gdffi", "gdfms",
                    "gdftrk", "h_ad_id", "hsCtaTracking", "hsa_acc", "hsa_ad", "hsa_cam", "hsa_grp", "hsa_kw", "hsa_mt", "hsa_net",
                    "hsa_src", "hsa_tgt", "hsa_ver", "igshid", "irclickid", "kb", "klar_adid", "klar_cpid", "klar_source",
                    "matomo_campaign", "matomo_cid", "matomo_content", "matomo_group", "matomo_keyword", "matomo_medium",
                    "matomo_placement", "matomo_source", "mc_cid", "mc_eid", "meta_placement", "meta_site_source",
                    "mkcid", "mkevt", "mkrid", "mkwid", "msclkid", "mtm_campaign", "mtm_cid", "mtm_content", "mtm_group",
                    "mtm_keyword", "mtm_medium", "mtm_placement", "mtm_source", "nb_adtype", "nb_ap", "nb_expid_meta", "nb_fii",
                    "nb_klid", "nb_kwd", "nb_li_ms", "nb_lp_ms", "nb_mi", "nb_mt", "nb_pc", "nb_pi", "nb_placement", "nb_ppi",
                    "nb_ti", "nbt", "ndclid", "padid", "pcrid", "piwik_campaign", "piwik_keyword", "piwik_kwd",
                    "pk_campaign", "pk_cid", "pk_content", "pk_keyword", "pk_kwd", "pk_medium", "pk_source", "pl_gc", "pp",
                    "redirect_log_mongo_id", "redirect_mongo_id", "rtid", "s_kwcid", "sb_referer_host", "scadid", "si", "sid",
                    "sms_click", "sms_source", "sms_uph", "srsltid", "toolid", "trk_contact", "trk_module", "trk_msg", "trk_sid",
                    "ttadid", "ttclid", "tw_adid", "tw_campaign", "tw_content", "tw_kwdid", "tw_source", "tw_term", "twclid",
                    "utm_campaign", "utm_content", "utm_creative_format", "utm_id", "utm_klaviyo_id", "utm_marketing_tactic",
                    "utm_medium", "utm_source", "utm_source_platform", "utm_term", "vmcid", "wbraid", "yclid" ),
            parse.excludeQueryParams() );
    }

}
