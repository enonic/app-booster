package com.enonic.app.booster.servlet;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.enonic.app.booster.servlet.RequestUtils.AcceptEncoding;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptEncodingParserTest
{
    private static AcceptEncoding parse( final String... headers )
    {
        return AcceptEncodingParser.parse( Collections.enumeration( List.of( headers ) ) );
    }

    private static AcceptEncoding parse( final Enumeration<String> headers )
    {
        return AcceptEncodingParser.parse( headers );
    }

    // ── null / empty header ───────────────────────────────────────────────

    @Test
    void null_enumeration_is_unspecified()
    {
        assertThat( parse( (Enumeration<String>) null ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void empty_enumeration_is_unspecified()
    {
        assertThat( parse() ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void empty_string_header_is_unspecified()
    {
        assertThat( parse( "" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void only_whitespace_header_is_unspecified()
    {
        assertThat( parse( "   \t  " ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    // ── happy path ────────────────────────────────────────────────────────

    @Test
    void br_only()
    {
        assertThat( parse( "br" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void gzip_only()
    {
        assertThat( parse( "gzip" ) ).isEqualTo( AcceptEncoding.GZIP );
    }

    @Test
    void br_preferred_over_gzip()
    {
        assertThat( parse( "gzip, br" ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "br, gzip" ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "gzip;q=1.0, br;q=0.1" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void br_rejected_falls_back_to_gzip()
    {
        assertThat( parse( "br;q=0, gzip" ) ).isEqualTo( AcceptEncoding.GZIP );
        assertThat( parse( "br;q=0, gzip;q=0.1" ) ).isEqualTo( AcceptEncoding.GZIP );
    }

    @Test
    void both_rejected_is_unspecified()
    {
        assertThat( parse( "br;q=0, gzip;q=0" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    // ── whitespace handling (OWS = SP/HTAB) ───────────────────────────────

    @Test
    void ows_around_comma_and_semicolon()
    {
        assertThat( parse( "br ; q=0.5 , gzip" ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "\tbr\t;\tq=0.5\t,\tgzip" ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "  br  ,  gzip  " ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void leading_and_trailing_ows_on_whole_value()
    {
        assertThat( parse( "   br   " ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "\tgzip\t" ) ).isEqualTo( AcceptEncoding.GZIP );
    }

    // ── case-insensitive encoding name ────────────────────────────────────

    @Test
    void case_insensitive_br()
    {
        assertThat( parse( "BR" ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "Br" ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "br" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void case_insensitive_gzip()
    {
        assertThat( parse( "GZIP" ) ).isEqualTo( AcceptEncoding.GZIP );
        assertThat( parse( "Gzip" ) ).isEqualTo( AcceptEncoding.GZIP );
        assertThat( parse( "gzip" ) ).isEqualTo( AcceptEncoding.GZIP );
    }

    @Test
    void case_insensitive_q_param_name()
    {
        assertThat( parse( "br;Q=0" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
        assertThat( parse( "br;Q=0, gzip" ) ).isEqualTo( AcceptEncoding.GZIP );
    }

    // ── q-value: accepted shapes (RFC 7231 §5.3.1) ────────────────────────

    @Test
    void qvalue_accepts_one_no_decimal()
    {
        assertThat( parse( "br;q=1" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void qvalue_accepts_one_dot_zero()
    {
        assertThat( parse( "br;q=1.0" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void qvalue_accepts_one_dot_zeros()
    {
        assertThat( parse( "br;q=1.000" ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "br;q=1.00" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void qvalue_accepts_one_dot_only()
    {
        assertThat( parse( "br;q=1." ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void qvalue_accepts_zero_dot_three_digits()
    {
        assertThat( parse( "br;q=0.001" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void qvalue_accepts_zero_dot_one_digit()
    {
        assertThat( parse( "br;q=0.5" ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "br;q=0.1" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    // ── q-value: rejected shapes → treated as q=0 ─────────────────────────

    @Test
    void qvalue_rejects_zero()
    {
        assertThat( parse( "br;q=0" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void qvalue_rejects_zero_dot_zero()
    {
        assertThat( parse( "br;q=0.0" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
        assertThat( parse( "br;q=0.000" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void qvalue_rejects_scientific_notation()
    {
        assertThat( parse( "br;q=1e-1" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void qvalue_rejects_nan()
    {
        assertThat( parse( "br;q=NaN" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void qvalue_rejects_signed()
    {
        assertThat( parse( "br;q=+0.5" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
        assertThat( parse( "br;q=-0.5" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void qvalue_rejects_empty()
    {
        assertThat( parse( "br;q=" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void qvalue_rejects_one_dot_nonzero()
    {
        assertThat( parse( "br;q=1.5" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
        assertThat( parse( "br;q=1.001" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void qvalue_rejects_too_many_decimals()
    {
        assertThat( parse( "br;q=0.1234" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
        assertThat( parse( "br;q=1.0000" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void qvalue_rejects_inner_whitespace()
    {
        assertThat( parse( "br;q= 0.5" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
        assertThat( parse( "br;q=0.5 " ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void qvalue_rejects_out_of_range_chars()
    {
        assertThat( parse( "br;q=2" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
        assertThat( parse( "br;q=0.a" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    // ── wildcard ──────────────────────────────────────────────────────────

    @Test
    void wildcard_implicit_q_treated_as_gzip()
    {
        assertThat( parse( "*" ) ).isEqualTo( AcceptEncoding.GZIP );
    }

    @Test
    void wildcard_with_quality_treated_as_gzip()
    {
        assertThat( parse( "*;q=0.5" ) ).isEqualTo( AcceptEncoding.GZIP );
    }

    @Test
    void wildcard_q_zero_is_unspecified()
    {
        assertThat( parse( "*;q=0" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void wildcard_does_not_elevate_brotli()
    {
        // br is not present, only `*` — must fall to gzip, never brotli.
        assertThat( parse( "*" ) ).isEqualTo( AcceptEncoding.GZIP );
    }

    @Test
    void wildcard_alongside_explicit_br_picks_brotli()
    {
        assertThat( parse( "br, *" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void wildcard_does_not_override_explicit_gzip_prohibition()
    {
        // RFC 9110 §12.5.3: `*` matches only codings not explicitly listed.
        // gzip is listed with q=0, so `*` cannot re-enable it.
        assertThat( parse( "gzip;q=0, *" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
        assertThat( parse( "*, gzip;q=0" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
        assertThat( parse( "br;q=0, gzip;q=0, *" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    // ── multiple header lines & multiple tokens ───────────────────────────

    @Test
    void multiple_header_lines()
    {
        assertThat( parse( "gzip", "br" ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "br;q=0", "gzip" ) ).isEqualTo( AcceptEncoding.GZIP );
        assertThat( parse( "deflate", "compress" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void multiple_tokens_in_one_line()
    {
        assertThat( parse( "br, gzip" ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "gzip;q=0.5, br;q=1.0" ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "br;q=0, gzip" ) ).isEqualTo( AcceptEncoding.GZIP );
    }

    // ── unknown encodings & identity ──────────────────────────────────────

    @Test
    void unknown_encodings_ignored()
    {
        assertThat( parse( "deflate, compress, identity" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void unknown_encodings_with_br_picks_brotli()
    {
        assertThat( parse( "deflate, br" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void unknown_encodings_with_gzip_picks_gzip()
    {
        assertThat( parse( "deflate, gzip" ) ).isEqualTo( AcceptEncoding.GZIP );
    }

    @Test
    void identity_only_is_unspecified()
    {
        // We only emit br/gzip/UNSPECIFIED — identity (always implicitly available per RFC) is
        // irrelevant for our compression-selection purpose.
        assertThat( parse( "identity" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    // ── extra params (RFC 7231 allows accept-ext after q) ─────────────────

    @Test
    void extra_params_after_q_are_ignored()
    {
        assertThat( parse( "br;q=0.5;something=else" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void params_before_q_are_ignored()
    {
        assertThat( parse( "br;foo=bar;q=0" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void only_first_q_counts()
    {
        assertThat( parse( "br;q=0;q=1" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
        assertThat( parse( "br;q=1;q=0" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    // ── edge cases ────────────────────────────────────────────────────────

    @Test
    void trailing_comma_tolerated()
    {
        assertThat( parse( "br," ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( ",br" ) ).isEqualTo( AcceptEncoding.BROTLI );
        assertThat( parse( "br,," ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void empty_token_between_commas_ignored()
    {
        assertThat( parse( "br, , gzip" ) ).isEqualTo( AcceptEncoding.BROTLI );
    }

    @Test
    void br_substring_not_matched()
    {
        assertThat( parse( "brotli" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
        assertThat( parse( "gzipx" ) ).isEqualTo( AcceptEncoding.UNSPECIFIED );
    }

    @Test
    void null_header_element_is_skipped()
    {
        final List<String> headers = Arrays.asList( null, "br" );
        assertThat( parse( Collections.enumeration( headers ) ) ).isEqualTo( AcceptEncoding.BROTLI );
    }
}
