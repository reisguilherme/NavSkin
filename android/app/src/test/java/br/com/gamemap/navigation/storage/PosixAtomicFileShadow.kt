package br.com.gamemap.navigation.storage

import android.util.AtomicFile
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Android's private AtomicFile.rename assumes POSIX replacement semantics. Robolectric on
 * Windows otherwise runs File.renameTo on NTFS, which refuses to replace an existing file.
 * Patch only that platform primitive; AtomicFile's real start/finish/recovery logic still runs.
 */
@Implements(AtomicFile::class)
class PosixAtomicFileShadow {
    companion object {
        var failNextRename = false

        @JvmStatic
        @Implementation
        fun rename(source: File, target: File) {
            if (failNextRename) {
                failNextRename = false
                // Android AtomicFile reports an unsuccessful rename through logging only.
                return
            }
            Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        }
    }
}
