package space.wavedot.core.domain

import space.wavedot.core.contract.MediaSource
import space.wavedot.core.model.MediaId
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.MediaSourceContractTest
import space.wavedot.core.testing.TestTracks

class FakeMediaSourceContractTest : MediaSourceContractTest() {
    override suspend fun createSource(): MediaSource = FakeMediaSource(tracks = TestTracks.tracks(5))

    override val knownTrackId: MediaId = TestTracks.track("1").id

    override val searchText: String = "Track"
}
