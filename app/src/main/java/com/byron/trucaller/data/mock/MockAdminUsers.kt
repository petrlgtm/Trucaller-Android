package com.byron.trucaller.data.mock

import com.byron.trucaller.data.model.AdminUser

val mockAdminUsers = listOf(
    AdminUser(
        id = "adm-001",
        name = "Admin Byron",
        phoneNumber = "+256787959715",
        email = "",
        password = "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9",
        role = "SUPER_ADMIN",
        lastLogin = "2025-12-01T10:00:00Z",
        createdAt = "2024-01-01T00:00:00Z"
    ),
    AdminUser(
        id = "adm-002",
        name = "Admin Two",
        phoneNumber = "+256751159472",
        email = "",
        password = "09fbcc458fb3430db7ec54ee95635e7fbc06ccfd982b49c55ec53ab8ac2397e7",
        role = "SUPER_ADMIN",
        lastLogin = "2025-11-30T09:00:00Z",
        createdAt = "2024-06-15T00:00:00Z"
    )
)
