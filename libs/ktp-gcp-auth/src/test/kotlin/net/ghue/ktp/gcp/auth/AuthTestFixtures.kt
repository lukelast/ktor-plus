package net.ghue.ktp.gcp.auth

import com.google.api.services.identitytoolkit.v2.IdentityToolkit
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2Config
import com.google.api.services.identitytoolkit.v2.model.GoogleCloudIdentitytoolkitAdminV2ListDefaultSupportedIdpConfigsResponse
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.mockk.every
import io.mockk.mockk

/** Strict [IdentityToolkit] mock serving [projectConfig] and [idpConfigResponse]. */
internal fun mockIdentityToolkit(
    projectConfig: GoogleCloudIdentitytoolkitAdminV2Config,
    idpConfigResponse: GoogleCloudIdentitytoolkitAdminV2ListDefaultSupportedIdpConfigsResponse,
): IdentityToolkitMock {
    val identityToolkit = mockk<IdentityToolkit>()
    val projects = mockk<IdentityToolkit.Projects>()
    val getConfigRequest = mockk<IdentityToolkit.Projects.GetConfig>()
    val idpConfigs = mockk<IdentityToolkit.Projects.DefaultSupportedIdpConfigs>()
    val listIdpConfigsRequest = mockk<IdentityToolkit.Projects.DefaultSupportedIdpConfigs.List>()

    every { identityToolkit.projects() } returns projects
    every { projects.getConfig("projects/test-project/config") } returns getConfigRequest
    every { getConfigRequest.execute() } returns projectConfig
    every { projects.defaultSupportedIdpConfigs() } returns idpConfigs
    every { idpConfigs.list("projects/test-project") } returns listIdpConfigsRequest
    every { listIdpConfigsRequest.setPageSize(any()) } returns listIdpConfigsRequest
    every { listIdpConfigsRequest.execute() } returns idpConfigResponse

    return IdentityToolkitMock(identityToolkit, getConfigRequest, listIdpConfigsRequest)
}

internal data class IdentityToolkitMock(
    val identityToolkit: IdentityToolkit,
    val getConfigRequest: IdentityToolkit.Projects.GetConfig,
    val listIdpConfigsRequest: IdentityToolkit.Projects.DefaultSupportedIdpConfigs.List,
)

/** [FirebaseApp] mock exposing only a project ID, for constructing the config service. */
internal fun mockFirebaseApp(projectId: String = "test-project"): FirebaseApp {
    val firebaseOptions = mockk<FirebaseOptions>()
    every { firebaseOptions.projectId } returns projectId
    val firebaseApp = mockk<FirebaseApp>()
    every { firebaseApp.options } returns firebaseOptions
    return firebaseApp
}
