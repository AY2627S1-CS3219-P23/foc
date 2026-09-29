// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: My Profile page, per web/docs/wireframes/profile.png — profile
// details, edit flows and delete account, all in the user domain's
// ProfileSection.
// Reviewed by: [pending]

import { ProfileSection } from '@/features/user/components/ProfileSection'

export function Profile() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold text-gray-900">My Profile</h1>
      <ProfileSection />
    </div>
  )
}
