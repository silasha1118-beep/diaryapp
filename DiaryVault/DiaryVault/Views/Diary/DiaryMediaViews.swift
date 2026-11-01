import SwiftUI
import AVFoundation
import AVKit
import PhotosUI
import UniformTypeIdentifiers

// MARK: - Composer attachment strip

struct DiaryComposerMediaBar: View {
    @Binding var attachments: [DiaryMediaAttachment]
    let entryID: UUID
    let localization: LocalizationManager
    var onPermissionDenied: (String) -> Void
    var onError: (String) -> Void

    @State private var isRecordingVoice = false
    @State private var voiceRecorder: VoiceRecorderController?
    @State private var voiceElapsed: TimeInterval = 0
    @State private var voiceTimer: Timer?
    @State private var showPhotoSource = false
    @State private var showVideoSource = false
    @State private var showCameraPhoto = false
    @State private var showCameraVideo = false
    @State private var photoPickerItem: PhotosPickerItem?
    @State private var videoPickerItem: PhotosPickerItem?
    @State private var showLibraryPhotoPicker = false
    @State private var showLibraryVideoPicker = false
    @State private var isImporting = false

    private var canAddMore: Bool {
        attachments.count < DiaryMediaConfig.maxAttachmentsPerEntry
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !attachments.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(attachments) { item in
                            attachmentChip(item)
                        }
                    }
                }
            }

            if isRecordingVoice {
                voiceRecordingRow
            } else {
                HStack(spacing: 18) {
                    mediaToolButton(
                        systemName: "mic.fill",
                        labelKey: "diary.media.voice",
                        disabled: !canAddMore
                    ) {
                        startVoiceRecording()
                    }
                    mediaToolButton(
                        systemName: "photo",
                        labelKey: "diary.media.photo",
                        disabled: !canAddMore
                    ) {
                        showPhotoSource = true
                    }
                    mediaToolButton(
                        systemName: "video",
                        labelKey: "diary.media.video",
                        disabled: !canAddMore
                    ) {
                        showVideoSource = true
                    }
                    Spacer(minLength: 0)
                    if isImporting {
                        ProgressView()
                            .controlSize(.small)
                    }
                }
            }
        }
        .confirmationDialog(
            localization.text("diary.media.photo.source"),
            isPresented: $showPhotoSource,
            titleVisibility: .visible
        ) {
            Button(localization.text("diary.media.takePhoto")) {
                requestCameraThen { showCameraPhoto = true }
            }
            Button(localization.text("diary.media.choosePhoto")) {
                showLibraryPhotoPicker = true
            }
            Button(localization.text("common.cancel"), role: .cancel) {}
        }
        .confirmationDialog(
            localization.text("diary.media.video.source"),
            isPresented: $showVideoSource,
            titleVisibility: .visible
        ) {
            Button(localization.text("diary.media.recordVideo")) {
                requestCameraThen { showCameraVideo = true }
            }
            Button(localization.text("diary.media.chooseVideo")) {
                showLibraryVideoPicker = true
            }
            Button(localization.text("common.cancel"), role: .cancel) {}
        }
        .photosPicker(
            isPresented: $showLibraryPhotoPicker,
            selection: $photoPickerItem,
            matching: .images,
            photoLibrary: .shared()
        )
        .photosPicker(
            isPresented: $showLibraryVideoPicker,
            selection: $videoPickerItem,
            matching: .videos,
            photoLibrary: .shared()
        )
        .onChange(of: photoPickerItem) { _, item in
            guard let item else { return }
            Task { await importPickedPhoto(item) }
        }
        .onChange(of: videoPickerItem) { _, item in
            guard let item else { return }
            Task { await importPickedVideo(item) }
        }
        .fullScreenCover(isPresented: $showCameraPhoto) {
            CameraCaptureView(mode: .photo) { result in
                showCameraPhoto = false
                handleCameraResult(result)
            }
            .ignoresSafeArea()
        }
        .fullScreenCover(isPresented: $showCameraVideo) {
            CameraCaptureView(mode: .video) { result in
                showCameraVideo = false
                handleCameraResult(result)
            }
            .ignoresSafeArea()
        }
    }

    private func mediaToolButton(
        systemName: String,
        labelKey: String,
        disabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: systemName)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(disabled ? VaultTheme.inkMuted.opacity(0.4) : VaultTheme.seal)
                    .frame(width: 36, height: 36)
                    .background(
                        Circle()
                            .fill(VaultTheme.seal.opacity(disabled ? 0.04 : 0.1))
                    )
                Text(localization.text(labelKey))
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(disabled ? VaultTheme.inkMuted.opacity(0.45) : VaultTheme.inkMuted)
            }
        }
        .buttonStyle(.plain)
        .disabled(disabled || isImporting)
        .accessibilityLabel(localization.text(labelKey))
    }

    private var voiceRecordingRow: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(VaultTheme.seal)
                .frame(width: 10, height: 10)
                .opacity(Int(voiceElapsed * 2) % 2 == 0 ? 1 : 0.35)
            Text(formatDuration(voiceElapsed))
                .font(.system(size: 15, weight: .medium, design: .monospaced))
                .foregroundStyle(VaultTheme.ink)
            Text(localization.text("diary.media.recording"))
                .font(.system(size: 13))
                .foregroundStyle(VaultTheme.inkMuted)
            Spacer()
            Button {
                stopVoiceRecording(save: false)
            } label: {
                Text(localization.text("common.cancel"))
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(VaultTheme.inkMuted)
            }
            .buttonStyle(.plain)
            Button {
                stopVoiceRecording(save: true)
            } label: {
                Text(localization.text("diary.media.stopSave"))
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(VaultTheme.paper)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(VaultTheme.seal)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 4)
    }

    private func attachmentChip(_ item: DiaryMediaAttachment) -> some View {
        HStack(spacing: 8) {
            if item.kind == .image,
               let ui = UIImage(contentsOfFile: DiaryMediaFileStore.absoluteURL(entryID: entryID, attachment: item).path) {
                Image(uiImage: ui)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 44, height: 44)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            } else {
                Image(systemName: item.kind.systemImage)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(VaultTheme.seal)
                    .frame(width: 44, height: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(VaultTheme.seal.opacity(0.1))
                    )
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(localization.text(labelKey(for: item.kind)))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(VaultTheme.ink)
                if let duration = item.durationSeconds {
                    Text(formatDuration(duration))
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(VaultTheme.inkMuted)
                }
            }
            Button {
                removeAttachment(item)
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(VaultTheme.inkMuted.opacity(0.7))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(localization.text("diary.media.remove"))
        }
        .padding(.trailing, 6)
        .padding(.vertical, 4)
        .padding(.leading, 4)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .stroke(VaultTheme.line.opacity(0.8), lineWidth: 1)
        )
    }

    private func labelKey(for kind: DiaryMediaKind) -> String {
        switch kind {
        case .voice: return "diary.media.voice"
        case .image: return "diary.media.photo"
        case .video: return "diary.media.video"
        }
    }

    private func formatDuration(_ value: TimeInterval) -> String {
        let total = max(0, Int(value.rounded()))
        return String(format: "%d:%02d", total / 60, total % 60)
    }

    // MARK: - Voice

    private func startVoiceRecording() {
        guard canAddMore else { return }
        Task {
            let granted = await VoiceRecorderController.requestPermission()
            guard granted else {
                onPermissionDenied(localization.text("diary.media.permission.mic"))
                return
            }
            do {
                let recorder = try VoiceRecorderController()
                try recorder.start()
                voiceRecorder = recorder
                isRecordingVoice = true
                voiceElapsed = 0
                voiceTimer?.invalidate()
                let timer = Timer(timeInterval: 0.25, repeats: true) { _ in
                    Task { @MainActor in
                        voiceElapsed = recorder.currentTime
                        if voiceElapsed >= DiaryMediaConfig.voiceMaxDurationSeconds {
                            stopVoiceRecording(save: true)
                        }
                    }
                }
                RunLoop.main.add(timer, forMode: .common)
                voiceTimer = timer
            } catch {
                onError(localization.text("diary.media.recordFailed"))
            }
        }
    }

    private func stopVoiceRecording(save: Bool) {
        voiceTimer?.invalidate()
        voiceTimer = nil
        let recorder = voiceRecorder
        voiceRecorder = nil
        isRecordingVoice = false
        let elapsed = voiceElapsed
        voiceElapsed = 0
        guard let recorder else { return }
        let url = recorder.stop()
        guard save, let url else {
            if let url { try? FileManager.default.removeItem(at: url) }
            return
        }
        do {
            let attachment = try DiaryMediaFileStore.importVoiceRecording(
                entryID: entryID,
                from: url,
                durationSeconds: elapsed
            )
            try? FileManager.default.removeItem(at: url)
            attachments.append(attachment)
        } catch {
            onError(localization.text("diary.media.saveFailed"))
        }
    }

    // MARK: - Photo / video import

    private func requestCameraThen(_ open: @escaping () -> Void) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            onError(localization.text("diary.media.cameraUnavailable"))
            return
        }
        Task {
            let status = AVCaptureDevice.authorizationStatus(for: .video)
            switch status {
            case .authorized:
                await MainActor.run { open() }
            case .notDetermined:
                let ok = await AVCaptureDevice.requestAccess(for: .video)
                await MainActor.run {
                    if ok { open() }
                    else { onPermissionDenied(localization.text("diary.media.permission.camera")) }
                }
            default:
                await MainActor.run {
                    onPermissionDenied(localization.text("diary.media.permission.camera"))
                }
            }
        }
    }

    private func handleCameraResult(_ result: CameraCaptureResult?) {
        guard let result else { return }
        isImporting = true
        Task {
            defer { Task { @MainActor in isImporting = false } }
            do {
                switch result {
                case .photo(let image):
                    let attachment = try DiaryMediaFileStore.saveImageJPEG(entryID: entryID, image: image)
                    await MainActor.run { attachments.append(attachment) }
                case .video(let url):
                    let attachment = try await DiaryMediaFileStore.importVideo(entryID: entryID, from: url)
                    await MainActor.run { attachments.append(attachment) }
                }
            } catch MediaFileError.videoTooLong {
                await MainActor.run { onError(localization.text("diary.media.videoTooLong")) }
            } catch {
                await MainActor.run { onError(localization.text("diary.media.saveFailed")) }
            }
        }
    }

    private func importPickedPhoto(_ item: PhotosPickerItem) async {
        isImporting = true
        defer {
            isImporting = false
            photoPickerItem = nil
        }
        do {
            guard let data = try await item.loadTransferable(type: Data.self),
                  let image = UIImage(data: data)
            else {
                onError(localization.text("diary.media.saveFailed"))
                return
            }
            let attachment = try DiaryMediaFileStore.saveImageJPEG(entryID: entryID, image: image)
            attachments.append(attachment)
        } catch {
            onError(localization.text("diary.media.saveFailed"))
        }
    }

    private func importPickedVideo(_ item: PhotosPickerItem) async {
        isImporting = true
        defer {
            isImporting = false
            videoPickerItem = nil
        }
        do {
            guard let movie = try await item.loadTransferable(type: PickedMovie.self) else {
                onError(localization.text("diary.media.saveFailed"))
                return
            }
            let attachment = try await DiaryMediaFileStore.importVideo(entryID: entryID, from: movie.url)
            attachments.append(attachment)
            try? FileManager.default.removeItem(at: movie.url)
        } catch MediaFileError.videoTooLong {
            onError(localization.text("diary.media.videoTooLong"))
        } catch {
            onError(localization.text("diary.media.saveFailed"))
        }
    }

    private func removeAttachment(_ item: DiaryMediaAttachment) {
        DiaryMediaFileStore.delete(entryID: entryID, attachment: item)
        attachments.removeAll { $0.id == item.id }
    }
}

// MARK: - Detail media gallery

struct DiaryEntryMediaGallery: View {
    let entry: DiaryEntry
    let localization: LocalizationManager

    var body: some View {
        if entry.hasMedia {
            VStack(alignment: .leading, spacing: 14) {
                ForEach(entry.attachments) { item in
                    switch item.kind {
                    case .image:
                        DiaryImageAttachmentView(entryID: entry.id, attachment: item)
                    case .voice:
                        DiaryAudioAttachmentView(
                            entryID: entry.id,
                            attachment: item,
                            localization: localization
                        )
                    case .video:
                        DiaryVideoAttachmentView(
                            entryID: entry.id,
                            attachment: item,
                            localization: localization
                        )
                    }
                }
            }
            .padding(.top, 8)
        }
    }
}

struct DiaryImageAttachmentView: View {
    let entryID: UUID
    let attachment: DiaryMediaAttachment
    @State private var image: UIImage?
    @State private var failed = false

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            } else if failed {
                mediaPlaceholder(systemName: "photo")
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
            }
        }
        .task {
            await load()
        }
    }

    private func load() async {
        do {
            let url = try await DiaryMediaCloudSync.ensureLocalFile(entryID: entryID, attachment: attachment)
            image = UIImage(contentsOfFile: url.path)
            if image == nil { failed = true }
        } catch {
            failed = true
        }
    }
}

struct DiaryAudioAttachmentView: View {
    let entryID: UUID
    let attachment: DiaryMediaAttachment
    let localization: LocalizationManager

    @State private var player: AVAudioPlayer?
    @State private var isPlaying = false
    @State private var failed = false
    @State private var progress: Double = 0
    @State private var tick: Timer?

    var body: some View {
        HStack(spacing: 12) {
            Button {
                togglePlay()
            } label: {
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(VaultTheme.paper)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(VaultTheme.seal))
            }
            .buttonStyle(.plain)
            .disabled(failed)

            VStack(alignment: .leading, spacing: 4) {
                Text(localization.text("diary.media.voice"))
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(VaultTheme.ink)
                if failed {
                    Text(localization.text("diary.media.unavailable"))
                        .font(.system(size: 12))
                        .foregroundStyle(VaultTheme.inkMuted)
                } else {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(VaultTheme.line.opacity(0.7))
                            Capsule()
                                .fill(VaultTheme.seal.opacity(0.85))
                                .frame(width: max(4, geo.size.width * progress))
                        }
                    }
                    .frame(height: 4)
                }
            }
            Spacer(minLength: 0)
            if let duration = attachment.durationSeconds ?? player?.duration {
                Text(formatDuration(duration))
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(VaultTheme.inkMuted)
            }
        }
        .padding(.vertical, 8)
        .task { await prepare() }
        .onDisappear { stopPlayback() }
    }

    private func prepare() async {
        do {
            let url = try await DiaryMediaCloudSync.ensureLocalFile(entryID: entryID, attachment: attachment)
            let audio = try AVAudioPlayer(contentsOf: url)
            audio.prepareToPlay()
            player = audio
        } catch {
            failed = true
        }
    }

    private func togglePlay() {
        guard let player else { return }
        if isPlaying {
            player.pause()
            isPlaying = false
            tick?.invalidate()
        } else {
            try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try? AVAudioSession.sharedInstance().setActive(true)
            player.play()
            isPlaying = true
            tick?.invalidate()
            tick = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { _ in
                guard player.duration > 0 else { return }
                progress = player.currentTime / player.duration
                if !player.isPlaying {
                    isPlaying = false
                    progress = 0
                    tick?.invalidate()
                }
            }
        }
    }

    private func stopPlayback() {
        tick?.invalidate()
        player?.stop()
        isPlaying = false
    }

    private func formatDuration(_ value: TimeInterval) -> String {
        let total = max(0, Int(value.rounded()))
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}

struct DiaryVideoAttachmentView: View {
    let entryID: UUID
    let attachment: DiaryMediaAttachment
    let localization: LocalizationManager
    @State private var player: AVPlayer?
    @State private var failed = false

    var body: some View {
        Group {
            if let player {
                VideoPlayer(player: player)
                    .frame(maxWidth: .infinity)
                    .frame(height: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            } else if failed {
                mediaPlaceholder(systemName: "video")
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
            }
        }
        .task { await load() }
        .onDisappear { player?.pause() }
    }

    private func load() async {
        do {
            let url = try await DiaryMediaCloudSync.ensureLocalFile(entryID: entryID, attachment: attachment)
            player = AVPlayer(url: url)
        } catch {
            failed = true
        }
    }
}

private func mediaPlaceholder(systemName: String) -> some View {
    HStack {
        Spacer()
        Image(systemName: systemName)
            .font(.system(size: 22))
            .foregroundStyle(VaultTheme.inkMuted)
            .padding(.vertical, 28)
        Spacer()
    }
    .background(
        RoundedRectangle(cornerRadius: 4, style: .continuous)
            .fill(VaultTheme.paperDeep.opacity(0.6))
    )
}

// MARK: - Voice recorder

final class VoiceRecorderController {
    private var recorder: AVAudioRecorder?
    private let fileURL: URL

    var currentTime: TimeInterval { recorder?.currentTime ?? 0 }

    init() throws {
        let tmp = FileManager.default.temporaryDirectory
            .appendingPathComponent("diary-voice-\(UUID().uuidString).m4a")
        fileURL = tmp
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
        try session.setActive(true)
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44_100,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
        ]
        recorder = try AVAudioRecorder(url: tmp, settings: settings)
        recorder?.prepareToRecord()
    }

    static func requestPermission() async -> Bool {
        await withCheckedContinuation { continuation in
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
    }

    func start() throws {
        guard recorder?.record() == true else {
            throw MediaFileError.encodeFailed
        }
    }

    func stop() -> URL? {
        recorder?.stop()
        recorder = nil
        return FileManager.default.fileExists(atPath: fileURL.path) ? fileURL : nil
    }
}

// MARK: - PhotosPicker movie transferable

struct PickedMovie: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { movie in
            SentTransferredFile(movie.url)
        } importing: { received in
            let temp = FileManager.default.temporaryDirectory
                .appendingPathComponent("diary-import-\(UUID().uuidString).mp4")
            if FileManager.default.fileExists(atPath: temp.path) {
                try FileManager.default.removeItem(at: temp)
            }
            try FileManager.default.copyItem(at: received.file, to: temp)
            return PickedMovie(url: temp)
        }
    }
}

// MARK: - Camera capture (UIKit)

enum CameraCaptureMode {
    case photo
    case video
}

enum CameraCaptureResult {
    case photo(UIImage)
    case video(URL)
}

struct CameraCaptureView: UIViewControllerRepresentable {
    let mode: CameraCaptureMode
    let onFinish: (CameraCaptureResult?) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = .camera
        switch mode {
        case .photo:
            picker.mediaTypes = ["public.image"]
            picker.cameraCaptureMode = .photo
        case .video:
            picker.mediaTypes = ["public.movie"]
            picker.cameraCaptureMode = .video
            picker.videoMaximumDuration = DiaryMediaConfig.videoMaxDurationSeconds
            picker.videoQuality = .typeMedium
        }
        picker.allowsEditing = false
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onFinish: (CameraCaptureResult?) -> Void
        init(onFinish: @escaping (CameraCaptureResult?) -> Void) {
            self.onFinish = onFinish
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onFinish(nil)
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage {
                onFinish(.photo(image))
                return
            }
            if let url = info[.mediaURL] as? URL {
                onFinish(.video(url))
                return
            }
            onFinish(nil)
        }
    }
}
