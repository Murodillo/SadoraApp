package uz.sadora.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform