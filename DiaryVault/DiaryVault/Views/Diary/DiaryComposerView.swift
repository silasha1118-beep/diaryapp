import SwiftUI

struct DiaryComposerView: View {
    @EnvironmentObject private var diaryStore: DiaryStore
    @EnvironmentObject private var localization: LocalizationManager
    @Environment(\.dismiss) private var dismiss

    @State private var title = ""
    @State private var bodyText = ""
    @State private var mood = ""

    private var canSave: Bool {
        !bodyText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            ZStack {
                VaultTheme.paper.ignoresSafeArea()

                VStack(alignment: .leading, spacing: 16) {
                    TextField(localization.text("diary.field.title"), text: $title)
                        .font(.system(size: 22, weight: .semibold, design: .serif))
                        .foregroundStyle(VaultTheme.ink)

                    TextField(localization.text("diary.field.mood"), text: $mood)
                        .font(.system(size: 14))
                        .foregroundStyle(VaultTheme.inkMuted)

                    Rectangle()
                        .fill(VaultTheme.line)
                        .frame(height: 1)

                    TextEditor(text: $bodyText)
                        .font(.system(size: 17, design: .serif))
                        .foregroundStyle(VaultTheme.ink)
                        .scrollContentBackground(.hidden)
                        .background(Color.clear)

                    Spacer(minLength: 0)
                }
                .padding(20)
            }
            .navigationTitle(localization.text("diary.new"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localization.text("common.cancel")) { dismiss() }
                        .foregroundStyle(VaultTheme.inkMuted)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localization.text("common.save")) {
                        diaryStore.add(
                            title: title,
                            body: bodyText,
                            moodTag: mood.isEmpty ? nil : mood
                        )
                        dismiss()
                    }
                    .disabled(!canSave)
                    .fontWeight(.semibold)
                    .foregroundStyle(canSave ? VaultTheme.seal : VaultTheme.inkMuted)
                }
            }
        }
    }
}
