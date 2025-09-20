package net.ghue.ktp.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EnumKtTest :
    StringSpec({
        "enumToCamelCase converts enum constants" {
            TestEnum.TEST_ENUM_01.enumToCamelCase() shouldBe "testEnum01"
            TestEnum.ABC2022_11_FRANK.enumToCamelCase() shouldBe "abc202211Frank"
        }
    })

private enum class TestEnum {
    TEST_ENUM_01,
    ABC2022_11_FRANK,
}
