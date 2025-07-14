package net.ghue.ktp.core

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class EnumKtTest {

    @Test
    fun enumToCamelCase() {
        Assertions.assertEquals("testEnum01", TestEnum.TEST_ENUM_01.enumToCamelCase())
        Assertions.assertEquals("abc202211Frank", TestEnum.ABC2022_11_FRANK.enumToCamelCase())
    }
}

private enum class TestEnum {
    TEST_ENUM_01,
    ABC2022_11_FRANK,
}
