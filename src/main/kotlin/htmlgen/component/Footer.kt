package htmlgen.component

import htmlgen.unsafeSVG
import kotlinx.html.*


val CC_BY_SA_4 =
    "<p xmlns:cc=\"http://creativecommons.org/ns#\" xmlns:dct=\"http://purl.org/dc/terms/\"><a property=\"dct:title\" rel=\"cc:attributionURL\" href=\"https://koiro.xyz/\">Koiro's Cat Café</a> by <a rel=\"cc:attributionURL dct:creator\" property=\"cc:attributionName\" href=\"https://koiro.xyz/\">Koiro</a> is licensed under <a href=\"https://creativecommons.org/licenses/by-sa/4.0/?ref=chooser-v1\" target=\"_blank\" rel=\"license noopener noreferrer\" style=\"display:inline-block;\">CC BY-SA 4.0<img style=\"height:22px!important;margin-left:3px;vertical-align:text-bottom;\" src=\"https://mirrors.creativecommons.org/presskit/icons/cc.svg?ref=chooser-v1\" alt=\"\"><img style=\"height:22px!important;margin-left:3px;vertical-align:text-bottom;\" src=\"https://mirrors.creativecommons.org/presskit/icons/by.svg?ref=chooser-v1\" alt=\"\"><img style=\"height:22px!important;margin-left:3px;vertical-align:text-bottom;\" src=\"https://mirrors.creativecommons.org/presskit/icons/sa.svg?ref=chooser-v1\" alt=\"\"></a></p>"

fun FlowContent.footer() {
    div {
        classes += "footer"
        div{
            p {
                +"本网站内容遵循协议 CC BY-SA 4.0"
            }
            div {
                unsafe {
                    +CC_BY_SA_4
                }
            }
        }

        div {

        }
    }
}