package com.example.mensajeria.data.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CountryPhoneInfo(
    val isoCode: String,
    val name: String,
    val dialCode: String,
    val flag: String,
    val maxDigits: Int
)

/**
 * Catálogo mundial de países con código telefónico internacional, longitud exacta de dígitos
 * y autodetección aproximada mediante la dirección IP del dispositivo.
 */
object CountryPhoneCatalog {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    val peru = CountryPhoneInfo("PE", "Perú", "+51", "🇵🇪", 9)

    val allCountries: List<CountryPhoneInfo> = listOf(
        CountryPhoneInfo("AF", "Afganistán", "+93", "🇦🇫", 9),
        CountryPhoneInfo("AL", "Albania", "+355", "🇦🇱", 9),
        CountryPhoneInfo("DE", "Alemania", "+49", "🇩🇪", 11),
        CountryPhoneInfo("AD", "Andorra", "+376", "🇦🇩", 6),
        CountryPhoneInfo("AO", "Angola", "+244", "🇦🇴", 9),
        CountryPhoneInfo("AG", "Antigua y Barbuda", "+1", "🇦🇬", 10),
        CountryPhoneInfo("SA", "Arabia Saudita", "+966", "🇸🇦", 9),
        CountryPhoneInfo("DZ", "Argelia", "+213", "🇩🇿", 9),
        CountryPhoneInfo("AR", "Argentina", "+54", "🇦🇷", 10),
        CountryPhoneInfo("AM", "Armenia", "+374", "🇦🇲", 8),
        CountryPhoneInfo("AU", "Australia", "+61", "🇦🇺", 9),
        CountryPhoneInfo("AT", "Austria", "+43", "🇦🇹", 10),
        CountryPhoneInfo("AZ", "Azerbaiyán", "+994", "🇦🇿", 9),
        CountryPhoneInfo("BS", "Bahamas", "+1", "🇧🇸", 10),
        CountryPhoneInfo("BD", "Bangladés", "+880", "🇧🇩", 10),
        CountryPhoneInfo("BB", "Barbados", "+1", "🇧🇧", 10),
        CountryPhoneInfo("BH", "Baréin", "+973", "🇧🇭", 8),
        CountryPhoneInfo("BE", "Bélgica", "+32", "🇧🇪", 9),
        CountryPhoneInfo("BZ", "Belice", "+501", "🇧🇿", 7),
        CountryPhoneInfo("BJ", "Benín", "+229", "🇧🇯", 8),
        CountryPhoneInfo("BY", "Bielorrusia", "+375", "🇧🇾", 9),
        CountryPhoneInfo("BO", "Bolivia", "+591", "🇧🇴", 8),
        CountryPhoneInfo("BA", "Bosnia y Herzegovina", "+387", "🇧🇦", 8),
        CountryPhoneInfo("BW", "Botsuana", "+267", "🇧🇼", 8),
        CountryPhoneInfo("BR", "Brasil", "+55", "🇧🇷", 11),
        CountryPhoneInfo("BN", "Brunéi", "+673", "🇧🇳", 7),
        CountryPhoneInfo("BG", "Bulgaria", "+359", "🇧🇬", 9),
        CountryPhoneInfo("BF", "Burkina Faso", "+226", "🇧🇫", 8),
        CountryPhoneInfo("BI", "Burundi", "+257", "🇧🇮", 8),
        CountryPhoneInfo("BT", "Bután", "+975", "🇧🇹", 8),
        CountryPhoneInfo("CV", "Cabo Verde", "+238", "🇨🇻", 7),
        CountryPhoneInfo("KH", "Camboya", "+855", "🇰🇭", 9),
        CountryPhoneInfo("CM", "Camerún", "+237", "🇨🇲", 9),
        CountryPhoneInfo("CA", "Canadá", "+1", "🇨🇦", 10),
        CountryPhoneInfo("QA", "Catar", "+974", "🇶🇦", 8),
        CountryPhoneInfo("TD", "Chad", "+235", "🇹🇩", 8),
        CountryPhoneInfo("CL", "Chile", "+56", "🇨🇱", 9),
        CountryPhoneInfo("CN", "China", "+86", "🇨🇳", 11),
        CountryPhoneInfo("CY", "Chipre", "+357", "🇨🇾", 8),
        CountryPhoneInfo("CO", "Colombia", "+57", "🇨🇴", 10),
        CountryPhoneInfo("KM", "Comoras", "+269", "🇰🇲", 7),
        CountryPhoneInfo("KP", "Corea del Norte", "+850", "🇰🇵", 10),
        CountryPhoneInfo("KR", "Corea del Sur", "+82", "🇰🇷", 10),
        CountryPhoneInfo("CI", "Costa de Marfil", "+225", "🇨🇮", 10),
        CountryPhoneInfo("CR", "Costa Rica", "+506", "🇨🇷", 8),
        CountryPhoneInfo("HR", "Croacia", "+385", "🇭🇷", 9),
        CountryPhoneInfo("CU", "Cuba", "+53", "🇨🇺", 8),
        CountryPhoneInfo("DK", "Dinamarca", "+45", "🇩🇰", 8),
        CountryPhoneInfo("DM", "Dominica", "+1", "🇩🇲", 10),
        CountryPhoneInfo("EC", "Ecuador", "+593", "🇪🇨", 9),
        CountryPhoneInfo("EG", "Egipto", "+20", "🇪🇬", 10),
        CountryPhoneInfo("SV", "El Salvador", "+503", "🇸🇻", 8),
        CountryPhoneInfo("AE", "Emiratos Árabes Unidos", "+971", "🇦🇪", 9),
        CountryPhoneInfo("ER", "Eritrea", "+291", "🇪🇷", 7),
        CountryPhoneInfo("SK", "Eslovaquia", "+421", "🇸🇰", 9),
        CountryPhoneInfo("SI", "Eslovenia", "+386", "🇸🇮", 8),
        CountryPhoneInfo("ES", "España", "+34", "🇪🇸", 9),
        CountryPhoneInfo("US", "Estados Unidos", "+1", "🇺🇸", 10),
        CountryPhoneInfo("EE", "Estonia", "+372", "🇪🇪", 8),
        CountryPhoneInfo("SZ", "Esuatini", "+268", "🇸🇿", 8),
        CountryPhoneInfo("ET", "Etiopía", "+251", "🇪🇹", 9),
        CountryPhoneInfo("PH", "Filipinas", "+63", "🇵🇭", 10),
        CountryPhoneInfo("FI", "Finlandia", "+358", "🇫🇮", 9),
        CountryPhoneInfo("FJ", "Fiyi", "+679", "🇫🇯", 7),
        CountryPhoneInfo("FR", "Francia", "+33", "🇫🇷", 9),
        CountryPhoneInfo("GA", "Gabón", "+241", "🇬🇦", 7),
        CountryPhoneInfo("GM", "Gambia", "+220", "🇬🇲", 7),
        CountryPhoneInfo("GE", "Georgia", "+995", "🇬🇪", 9),
        CountryPhoneInfo("GH", "Ghana", "+233", "🇬🇭", 9),
        CountryPhoneInfo("GD", "Granada", "+1", "🇬🇩", 10),
        CountryPhoneInfo("GR", "Grecia", "+30", "🇬🇷", 10),
        CountryPhoneInfo("GT", "Guatemala", "+502", "🇬🇹", 8),
        CountryPhoneInfo("GN", "Guinea", "+224", "🇬🇳", 9),
        CountryPhoneInfo("GW", "Guinea-Bisáu", "+245", "🇬🇼", 7),
        CountryPhoneInfo("GQ", "Guinea Ecuatorial", "+240", "🇬🇶", 9),
        CountryPhoneInfo("GY", "Guyana", "+592", "🇬🇾", 7),
        CountryPhoneInfo("HT", "Haití", "+509", "🇭🇹", 8),
        CountryPhoneInfo("HN", "Honduras", "+504", "🇭🇳", 8),
        CountryPhoneInfo("HU", "Hungría", "+36", "🇭🇺", 9),
        CountryPhoneInfo("IN", "India", "+91", "🇮🇳", 10),
        CountryPhoneInfo("ID", "Indonesia", "+62", "🇮🇩", 11),
        CountryPhoneInfo("IQ", "Irak", "+964", "🇮🇶", 10),
        CountryPhoneInfo("IR", "Irán", "+98", "🇮🇷", 10),
        CountryPhoneInfo("IE", "Irlanda", "+353", "🇮🇪", 9),
        CountryPhoneInfo("IS", "Islandia", "+354", "🇮🇸", 7),
        CountryPhoneInfo("IL", "Israel", "+972", "🇮🇱", 9),
        CountryPhoneInfo("IT", "Italia", "+39", "🇮🇹", 10),
        CountryPhoneInfo("JM", "Jamaica", "+1", "🇯🇲", 10),
        CountryPhoneInfo("JP", "Japón", "+81", "🇯🇵", 10),
        CountryPhoneInfo("JO", "Jordania", "+962", "🇯🇴", 9),
        CountryPhoneInfo("KZ", "Kazajistán", "+7", "🇰🇿", 10),
        CountryPhoneInfo("KE", "Kenia", "+254", "🇰🇪", 9),
        CountryPhoneInfo("KG", "Kirguistán", "+996", "🇰🇬", 9),
        CountryPhoneInfo("KI", "Kiribati", "+686", "🇰🇮", 8),
        CountryPhoneInfo("KW", "Kuwait", "+965", "🇰🇼", 8),
        CountryPhoneInfo("LA", "Laos", "+856", "🇱🇦", 10),
        CountryPhoneInfo("LS", "Lesoto", "+266", "🇱🇸", 8),
        CountryPhoneInfo("LV", "Letonia", "+371", "🇱🇻", 8),
        CountryPhoneInfo("LB", "Líbano", "+961", "🇱🇧", 8),
        CountryPhoneInfo("LR", "Liberia", "+231", "🇱🇷", 8),
        CountryPhoneInfo("LY", "Libia", "+218", "🇱🇾", 9),
        CountryPhoneInfo("LI", "Liechtenstein", "+423", "🇱🇮", 7),
        CountryPhoneInfo("LT", "Lituania", "+370", "🇱🇹", 8),
        CountryPhoneInfo("LU", "Luxemburgo", "+352", "🇱🇺", 9),
        CountryPhoneInfo("MK", "Macedonia del Norte", "+389", "🇲🇰", 8),
        CountryPhoneInfo("MG", "Madagascar", "+261", "🇲🇬", 9),
        CountryPhoneInfo("MY", "Malasia", "+60", "🇲🇾", 10),
        CountryPhoneInfo("MW", "Malaui", "+265", "🇲🇼", 9),
        CountryPhoneInfo("MV", "Maldivas", "+960", "🇲🇻", 7),
        CountryPhoneInfo("ML", "Malí", "+223", "🇲🇱", 8),
        CountryPhoneInfo("MT", "Malta", "+356", "🇲🇹", 8),
        CountryPhoneInfo("MA", "Marruecos", "+212", "🇲🇦", 9),
        CountryPhoneInfo("MU", "Mauricio", "+230", "🇲🇺", 8),
        CountryPhoneInfo("MR", "Mauritania", "+222", "🇲🇷", 8),
        CountryPhoneInfo("MX", "México", "+52", "🇲🇽", 10),
        CountryPhoneInfo("FM", "Micronesia", "+691", "🇫🇲", 7),
        CountryPhoneInfo("MD", "Moldavia", "+373", "🇲🇩", 8),
        CountryPhoneInfo("MC", "Mónaco", "+377", "🇲🇨", 8),
        CountryPhoneInfo("MN", "Mongolia", "+976", "🇲🇳", 8),
        CountryPhoneInfo("ME", "Montenegro", "+382", "🇲🇪", 8),
        CountryPhoneInfo("MZ", "Mozambique", "+258", "🇲🇿", 9),
        CountryPhoneInfo("MM", "Myanmar", "+95", "🇲🇲", 9),
        CountryPhoneInfo("NA", "Namibia", "+264", "🇳🇦", 9),
        CountryPhoneInfo("NR", "Nauru", "+674", "🇳🇷", 7),
        CountryPhoneInfo("NP", "Nepal", "+977", "🇳🇵", 10),
        CountryPhoneInfo("NI", "Nicaragua", "+505", "🇳🇮", 8),
        CountryPhoneInfo("NE", "Níger", "+227", "🇳🇪", 8),
        CountryPhoneInfo("NG", "Nigeria", "+234", "🇳🇬", 10),
        CountryPhoneInfo("NO", "Noruega", "+47", "🇳🇴", 8),
        CountryPhoneInfo("NZ", "Nueva Zelanda", "+64", "🇳🇿", 9),
        CountryPhoneInfo("OM", "Omán", "+968", "🇴🇲", 8),
        CountryPhoneInfo("NL", "Países Bajos", "+31", "🇳🇱", 9),
        CountryPhoneInfo("PK", "Pakistán", "+92", "🇵🇰", 10),
        CountryPhoneInfo("PW", "Palaos", "+680", "🇵🇼", 7),
        CountryPhoneInfo("PA", "Panamá", "+507", "🇵🇦", 8),
        CountryPhoneInfo("PG", "Papúa Nueva Guinea", "+675", "🇵🇬", 8),
        CountryPhoneInfo("PY", "Paraguay", "+595", "🇵🇾", 9),
        peru,
        CountryPhoneInfo("PL", "Polonia", "+48", "🇵🇱", 9),
        CountryPhoneInfo("PT", "Portugal", "+351", "🇵🇹", 9),
        CountryPhoneInfo("PR", "Puerto Rico", "+1", "🇵🇷", 10),
        CountryPhoneInfo("GB", "Reino Unido", "+44", "🇬🇧", 10),
        CountryPhoneInfo("CF", "República Centroafricana", "+236", "🇨🇫", 8),
        CountryPhoneInfo("CZ", "República Checa", "+420", "🇨🇿", 9),
        CountryPhoneInfo("CG", "República del Congo", "+242", "🇨🇬", 9),
        CountryPhoneInfo("CD", "República Democrática del Congo", "+243", "🇨🇩", 9),
        CountryPhoneInfo("DO", "República Dominicana", "+1", "🇩🇴", 10),
        CountryPhoneInfo("RW", "Ruanda", "+250", "🇷🇼", 9),
        CountryPhoneInfo("RO", "Rumania", "+40", "🇷🇴", 9),
        CountryPhoneInfo("RU", "Rusia", "+7", "🇷🇺", 10),
        CountryPhoneInfo("WS", "Samoa", "+685", "🇼🇸", 7),
        CountryPhoneInfo("KN", "San Cristóbal y Nieves", "+1", "🇰🇳", 10),
        CountryPhoneInfo("SM", "San Marino", "+378", "🇸🇲", 10),
        CountryPhoneInfo("VC", "San Vicente y las Granadinas", "+1", "🇻🇨", 10),
        CountryPhoneInfo("LC", "Santa Lucía", "+1", "🇱🇨", 10),
        CountryPhoneInfo("ST", "Santo Tomé y Príncipe", "+239", "🇸🇹", 7),
        CountryPhoneInfo("SN", "Senegal", "+221", "🇸🇳", 9),
        CountryPhoneInfo("RS", "Serbia", "+381", "🇷🇸", 9),
        CountryPhoneInfo("SC", "Seychelles", "+248", "🇸🇨", 7),
        CountryPhoneInfo("SL", "Sierra Leona", "+232", "🇸🇱", 8),
        CountryPhoneInfo("SG", "Singapur", "+65", "🇸🇬", 8),
        CountryPhoneInfo("SY", "Siria", "+963", "🇸🇾", 9),
        CountryPhoneInfo("SO", "Somalia", "+252", "🇸🇴", 8),
        CountryPhoneInfo("LK", "Sri Lanka", "+94", "🇱🇰", 9),
        CountryPhoneInfo("ZA", "Sudáfrica", "+27", "🇿🇦", 9),
        CountryPhoneInfo("SD", "Sudán", "+249", "🇸🇩", 9),
        CountryPhoneInfo("SS", "Sudán del Sur", "+211", "🇸🇸", 9),
        CountryPhoneInfo("SE", "Suecia", "+46", "🇸🇪", 9),
        CountryPhoneInfo("CH", "Suiza", "+41", "🇨🇭", 9),
        CountryPhoneInfo("SR", "Surinam", "+597", "🇸🇷", 7),
        CountryPhoneInfo("TH", "Tailandia", "+66", "🇹🇭", 9),
        CountryPhoneInfo("TW", "Taiwán", "+886", "🇹🇼", 9),
        CountryPhoneInfo("TZ", "Tanzania", "+255", "🇹🇿", 9),
        CountryPhoneInfo("TJ", "Tayikistán", "+992", "🇹🇯", 9),
        CountryPhoneInfo("TL", "Timor Oriental", "+670", "🇹🇱", 8),
        CountryPhoneInfo("TG", "Togo", "+228", "🇹🇬", 8),
        CountryPhoneInfo("TO", "Tonga", "+676", "🇹🇴", 7),
        CountryPhoneInfo("TT", "Trinidad y Tobago", "+1", "🇹🇹", 10),
        CountryPhoneInfo("TN", "Túnez", "+216", "🇹🇳", 8),
        CountryPhoneInfo("TM", "Turkmenistán", "+993", "🇹🇲", 8),
        CountryPhoneInfo("TR", "Turquía", "+90", "🇹🇷", 10),
        CountryPhoneInfo("TV", "Tuvalu", "+688", "🇹🇻", 6),
        CountryPhoneInfo("UA", "Ucrania", "+380", "🇺🇦", 9),
        CountryPhoneInfo("UG", "Uganda", "+256", "🇺🇬", 9),
        CountryPhoneInfo("UY", "Uruguay", "+598", "🇺🇾", 8),
        CountryPhoneInfo("UZ", "Uzbekistán", "+998", "🇺🇿", 9),
        CountryPhoneInfo("VU", "Vanuatu", "+678", "🇻🇺", 7),
        CountryPhoneInfo("VA", "Vaticano", "+379", "🇻🇦", 10),
        CountryPhoneInfo("VE", "Venezuela", "+58", "🇻🇪", 10),
        CountryPhoneInfo("VN", "Vietnam", "+84", "🇻🇳", 9),
        CountryPhoneInfo("YE", "Yemen", "+967", "🇾🇪", 9),
        CountryPhoneInfo("DJ", "Yibuti", "+253", "🇩🇯", 8),
        CountryPhoneInfo("ZM", "Zambia", "+260", "🇿🇲", 9),
        CountryPhoneInfo("ZW", "Zimbabue", "+263", "🇿🇼", 9)
    )

    fun findByIso(iso: String): CountryPhoneInfo {
        val code = iso.trim().uppercase()
        return allCountries.firstOrNull { it.isoCode == code } ?: peru
    }

    /**
     * Detecta el código ISO de país aproximado usando la IP pública del dispositivo.
     * Si no hay conectividad o falla, usa el Locale del dispositivo o Perú (PE).
     */
    suspend fun detectCountryIsoByIp(): String = withContext(Dispatchers.IO) {
        val fromIpApi = try {
            val req = Request.Builder().url("http://ip-api.com/json/?fields=countryCode").build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    JSONObject(body).optString("countryCode").takeIf { it.length == 2 }
                } else null
            }
        } catch (_: Exception) {
            null
        }
        if (!fromIpApi.isNullOrBlank()) return@withContext fromIpApi.uppercase()

        val fromIpapiCo = try {
            val req = Request.Builder().url("https://ipapi.co/country/").build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    resp.body?.string()?.trim()?.takeIf { it.length == 2 }
                } else null
            }
        } catch (_: Exception) {
            null
        }
        if (!fromIpapiCo.isNullOrBlank()) return@withContext fromIpapiCo.uppercase()

        return@withContext Locale.getDefault().country.takeIf { it.length == 2 } ?: "PE"
    }
}
