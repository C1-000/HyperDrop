package cythen.hyperdrop.config

import cythen.hyperdrop.R

/**
 * Built-in earbud art indexed by a device imageKey (the asset slug).
 * Each entry resolves to three drawable ids (box / left / right).
 * One-piece models (over-ear / neckband) only have box art, so left and right
 * point to the same main image. Unknown keys fall back to [DEFAULT].
 *
 * Display priority: user custom image path > built-in device set > [DEFAULT].
 */
data class DeviceImageSet(val box: Int, val left: Int, val right: Int)

object DeviceImageCatalog {
    /** Generic art used for fallback and for models without dedicated assets. */
    val DEFAULT = DeviceImageSet(R.drawable.img_box, R.drawable.img_left, R.drawable.img_right)

    /** Resolve the built-in set for a device imageKey (asset slug). */
    fun resolve(imageKey: String?): DeviceImageSet = when (imageKey) {
        "bianca_stigmata" -> onePiece(R.drawable.dev_bianca_stigmata_box)
        "h_i_d_e_404_klukai" -> trio(
            R.drawable.dev_h_i_d_e_404_klukai_box,
            R.drawable.dev_h_i_d_e_404_klukai_left,
            R.drawable.dev_h_i_d_e_404_klukai_right,
        )
        "laplace_oba" -> trio(
            R.drawable.dev_laplace_oba_box,
            R.drawable.dev_laplace_oba_left,
            R.drawable.dev_laplace_oba_right,
        )
        "me_163_komet_moca" -> trio(
            R.drawable.dev_me_163_komet_moca_box,
            R.drawable.dev_me_163_komet_moca_left,
            R.drawable.dev_me_163_komet_moca_right,
        )
        "moondrop_alice" -> trio(
            R.drawable.dev_moondrop_alice_box,
            R.drawable.dev_moondrop_alice_left,
            R.drawable.dev_moondrop_alice_right,
        )
        "moondrop_dots" -> trio(
            R.drawable.dev_moondrop_dots_box,
            R.drawable.dev_moondrop_dots_left,
            R.drawable.dev_moondrop_dots_right,
        )
        "moondrop_edge" -> onePiece(R.drawable.dev_moondrop_edge_box)
        "moondrop_edge_2" -> onePiece(R.drawable.dev_moondrop_edge_2_box)
        "moondrop_evo_2" -> trio(
            R.drawable.dev_moondrop_evo_2_box,
            R.drawable.dev_moondrop_evo_2_left,
            R.drawable.dev_moondrop_evo_2_right,
        )
        "moondrop_golden_ages" -> trio(
            R.drawable.dev_moondrop_golden_ages_box,
            R.drawable.dev_moondrop_golden_ages_left,
            R.drawable.dev_moondrop_golden_ages_right,
        )
        "moondrop_golden_ages_2" -> trio(
            R.drawable.dev_moondrop_golden_ages_2_box,
            R.drawable.dev_moondrop_golden_ages_2_left,
            R.drawable.dev_moondrop_golden_ages_2_right,
        )
        "moondrop_littlewhite" -> onePiece(R.drawable.dev_moondrop_littlewhite_box)
        "moondrop_mirage" -> trio(
            R.drawable.dev_moondrop_mirage_box,
            R.drawable.dev_moondrop_mirage_left,
            R.drawable.dev_moondrop_mirage_right,
        )
        "moondrop_mm3a" -> trio(
            R.drawable.dev_moondrop_mm3a_box,
            R.drawable.dev_moondrop_mm3a_left,
            R.drawable.dev_moondrop_mm3a_right,
        )
        "moondrop_moca" -> trio(
            R.drawable.dev_moondrop_moca_box,
            R.drawable.dev_moondrop_moca_left,
            R.drawable.dev_moondrop_moca_right,
        )
        "moondrop_moon_travel" -> trio(
            R.drawable.dev_moondrop_moon_travel_box,
            R.drawable.dev_moondrop_moon_travel_left,
            R.drawable.dev_moondrop_moon_travel_right,
        )
        "moondrop_nekocake" -> trio(
            R.drawable.dev_moondrop_nekocake_box,
            R.drawable.dev_moondrop_nekocake_left,
            R.drawable.dev_moondrop_nekocake_right,
        )
        "moondrop_nekocake_acht_acht_limited" -> trio(
            R.drawable.dev_moondrop_nekocake_acht_acht_limited_box,
            R.drawable.dev_moondrop_nekocake_acht_acht_limited_left,
            R.drawable.dev_moondrop_nekocake_acht_acht_limited_right,
        )
        "moondrop_nekocake_qbz_191" -> trio(
            R.drawable.dev_moondrop_nekocake_qbz_191_box,
            R.drawable.dev_moondrop_nekocake_qbz_191_left,
            R.drawable.dev_moondrop_nekocake_qbz_191_right,
        )
        "moondrop_pill" -> trio(
            R.drawable.dev_moondrop_pill_box,
            R.drawable.dev_moondrop_pill_left,
            R.drawable.dev_moondrop_pill_right,
        )
        "moondrop_pudding" -> trio(
            R.drawable.dev_moondrop_pudding_box,
            R.drawable.dev_moondrop_pudding_left,
            R.drawable.dev_moondrop_pudding_right,
        )
        "moondrop_punishing_gray_raven" -> trio(
            R.drawable.dev_moondrop_punishing_gray_raven_box,
            R.drawable.dev_moondrop_punishing_gray_raven_left,
            R.drawable.dev_moondrop_punishing_gray_raven_right,
        )
        "moondrop_space_travel" -> trio(
            R.drawable.dev_moondrop_space_travel_box,
            R.drawable.dev_moondrop_space_travel_left,
            R.drawable.dev_moondrop_space_travel_right,
        )
        "moondrop_sparks" -> trio(
            R.drawable.dev_moondrop_sparks_box,
            R.drawable.dev_moondrop_sparks_left,
            R.drawable.dev_moondrop_sparks_right,
        )
        "moondrop_sparxie" -> trio(
            R.drawable.dev_moondrop_sparxie_box,
            R.drawable.dev_moondrop_sparxie_left,
            R.drawable.dev_moondrop_sparxie_right,
        )
        "moondrop_susanoo_tws" -> trio(
            R.drawable.dev_moondrop_susanoo_tws_box,
            R.drawable.dev_moondrop_susanoo_tws_left,
            R.drawable.dev_moondrop_susanoo_tws_right,
        )
        "moondrop_the_garden" -> trio(
            R.drawable.dev_moondrop_the_garden_box,
            R.drawable.dev_moondrop_the_garden_left,
            R.drawable.dev_moondrop_the_garden_right,
        )
        "moondrop_u_c_t_s" -> trio(
            R.drawable.dev_moondrop_u_c_t_s_box,
            R.drawable.dev_moondrop_u_c_t_s_left,
            R.drawable.dev_moondrop_u_c_t_s_right,
        )
        "moondrop_ultrasonic" -> trio(
            R.drawable.dev_moondrop_ultrasonic_box,
            R.drawable.dev_moondrop_ultrasonic_left,
            R.drawable.dev_moondrop_ultrasonic_right,
        )
        "moondrop_voyager" -> onePiece(R.drawable.dev_moondrop_voyager_box)
        "moondrop_x_yasuno_kiyono" -> trio(
            R.drawable.dev_moondrop_x_yasuno_kiyono_box,
            R.drawable.dev_moondrop_x_yasuno_kiyono_left,
            R.drawable.dev_moondrop_x_yasuno_kiyono_right,
        )
        "nicole_reeyn" -> onePiece(R.drawable.dev_nicole_reeyn_box)
        "pandaer_open_air_pill" -> trio(
            R.drawable.dev_pandaer_open_air_pill_box,
            R.drawable.dev_pandaer_open_air_pill_left,
            R.drawable.dev_pandaer_open_air_pill_right,
        )
        "pandaer_space_travel_2" -> trio(
            R.drawable.dev_pandaer_space_travel_2_box,
            R.drawable.dev_pandaer_space_travel_2_left,
            R.drawable.dev_pandaer_space_travel_2_right,
        )
        "pill_gotoh_hitori" -> trio(
            R.drawable.dev_pill_gotoh_hitori_box,
            R.drawable.dev_pill_gotoh_hitori_left,
            R.drawable.dev_pill_gotoh_hitori_right,
        )
        "pill_ijichi_nijika" -> trio(
            R.drawable.dev_pill_ijichi_nijika_box,
            R.drawable.dev_pill_ijichi_nijika_left,
            R.drawable.dev_pill_ijichi_nijika_right,
        )
        "pill_kita_ikuyo" -> trio(
            R.drawable.dev_pill_kita_ikuyo_box,
            R.drawable.dev_pill_kita_ikuyo_left,
            R.drawable.dev_pill_kita_ikuyo_right,
        )
        "pill_yamada_ryo" -> trio(
            R.drawable.dev_pill_yamada_ryo_box,
            R.drawable.dev_pill_yamada_ryo_left,
            R.drawable.dev_pill_yamada_ryo_right,
        )
        "punishing_gray_raven" -> trio(
            R.drawable.dev_punishing_gray_raven_box,
            R.drawable.dev_punishing_gray_raven_left,
            R.drawable.dev_punishing_gray_raven_right,
        )
        "robin_s_earphones" -> trio(
            R.drawable.dev_robin_s_earphones_box,
            R.drawable.dev_robin_s_earphones_left,
            R.drawable.dev_robin_s_earphones_right,
        )
        "space_force" -> trio(
            R.drawable.dev_space_force_box,
            R.drawable.dev_space_force_left,
            R.drawable.dev_space_force_right,
        )
        "space_travel_2" -> trio(
            R.drawable.dev_space_travel_2_box,
            R.drawable.dev_space_travel_2_left,
            R.drawable.dev_space_travel_2_right,
        )
        "space_travel_2_ultra" -> trio(
            R.drawable.dev_space_travel_2_ultra_box,
            R.drawable.dev_space_travel_2_ultra_left,
            R.drawable.dev_space_travel_2_ultra_right,
        )
        "susanoo_tws" -> trio(
            R.drawable.dev_susanoo_tws_box,
            R.drawable.dev_susanoo_tws_left,
            R.drawable.dev_susanoo_tws_right,
        )
        "zzz_angels_ows" -> trio(
            R.drawable.dev_zzz_angels_ows_box,
            R.drawable.dev_zzz_angels_ows_left,
            R.drawable.dev_zzz_angels_ows_right,
        )
        else -> DEFAULT
    }

    private fun trio(box: Int, left: Int, right: Int) = DeviceImageSet(box, left, right)

    private fun onePiece(box: Int) = DeviceImageSet(box, box, box)
}
