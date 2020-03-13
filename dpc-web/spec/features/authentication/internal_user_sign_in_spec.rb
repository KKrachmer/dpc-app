# frozen_string_literal: true

require 'rails_helper'

RSpec.feature 'internal user signs in' do
  include OauthSupport

  context 'with github oauth' do
    around do |example|
      OmniAuth.config.test_mode = true

      ClimateControl.modify GITHUB_ORG_TEAM_ID: '111222333', INTERNAL_AUTH_PROVIDER: 'github' do
        example.run
      end

      OmniAuth.config.mock_auth[:github] = nil
    end

    before(:each) do
      stub_const('InternalUser::GITHUB_AUTH_ENABLED', true)
      stub_const('InternalUser::OKTA_AUTH_ENABLED', false)
    end

    context 'when user has valid github org and team' do
      before(:each) do
        github_client = double(Octokit::Client)
        allow(Octokit::Client).to receive(:new).and_return(github_client)
        allow(github_client).to receive(:user_teams).and_return(
          [
            {
              name: 'dpc-test',
              id: '111222333',
              slug: 'dpc-test',
              privacy: 'closed',
              url: 'https://api.github.com/teams/111222333',
              organization: {
                login: 'CMSgov',
                id: '999888777',
                url: 'https://api.github.com/orgs/CMSgov'
              }
            }
          ]
        )
      end

      scenario 'creates new internal user' do
        OmniAuth.config.mock_auth[:github] = OmniAuth::AuthHash.new(
          provider: 'github',
          uid: '123545',
          info: {
            nickname: 'whereisnemo',
            email: 'test@cms.hhs.gov',
            name: 'New Nemo',
            image: 'https://avatars3.githubusercontent.com/u/111'
          },
          credentials: { token: 'abcdefg' }
        )

        visit new_internal_user_session_path
        find('[data-test="internal-user-sign-in-form"]').click

        expect(page).to have_css('[data-test="internal-user-signout"]')
        expect(page).to have_content('test@cms.hhs.gov')
      end

      scenario 'logs in returning internal user' do
        OmniAuth.config.mock_auth[:github] = OmniAuth::AuthHash.new(
          provider: 'github',
          uid: '56789',
          info: {
            nickname: 'foundnemo',
            email: 'nemo@cms.hhs.gov',
            image: 'https://avatars3.githubusercontent.com/u/111'
          },
          credentials: { token: 'abcdefg' }
        )

        internal_user = create(:internal_user, provider: 'github', uid: '56789', name: 'Found Nemo')

        visit new_internal_user_session_path
        find('[data-test="internal-user-sign-in-form"]').click

        expect(page).to have_css('[data-test="internal-user-signout"]')
        expect(page).to have_content(internal_user.email)
      end

      scenario 'internal user cannot then sign in as user' do
        OmniAuth.config.mock_auth[:github] = OmniAuth::AuthHash.new(
          provider: 'github',
          uid: '123545',
          info: {
            nickname: 'whereisnemo',
            email: 'test@cms.hhs.gov',
            name: 'Nemo',
            image: 'https://avatars3.githubusercontent.com/u/111'
          },
          credentials: { token: 'abcdefg' }
        )

        visit new_internal_user_session_path
        find('[data-test="internal-user-sign-in-form"]').click

        expect(page).to have_css('[data-test="internal-user-signout"]')

        visit new_user_session_path

        expect(page).not_to have_css('[data-test="user-sign-in-form"]')
        expect(page).to have_css('[data-test="internal-user-signout"]')
      end
    end

    context 'when unsuccessful' do
      context 'when user has valid github credentials' do
        before(:each) do
          OmniAuth.config.mock_auth[:github] = OmniAuth::AuthHash.new(
            provider: 'github',
            uid: '123545',
            info: {
              nickname: 'whereisnemo',
              email: 'test@cms.hhs.gov',
              name: 'Nemo',
              image: 'https://avatars3.githubusercontent.com/u/111'
            },
            credentials: { token: 'abcdefg' }
          )
        end

        scenario 'when user does not have any github teams' do
          github_client = double(Octokit::Client)
          allow(Octokit::Client).to receive(:new).and_return(github_client)
          allow(github_client).to receive(:user_teams).and_return([])

          visit new_internal_user_session_path
          find('[data-test="internal-user-sign-in-form"]').click

          expect(page).to have_css('[data-test="internal-user-sign-in-form"]')
          expect(page).not_to have_css('[data-test="internal-user-signout"]')
        end

        scenario 'when user does not have a valid github team' do
          github_client = double(Octokit::Client)
          allow(Octokit::Client).to receive(:new).and_return(github_client)
          allow(github_client).to receive(:user_teams).and_return(
            [
              {
                name: 'invalidteam',
                id: '77777777',
                slug: 'invalidteam',
                privacy: 'closed',
                url: 'https://api.github.com/teams/77777777',
                organization: {
                  login: 'CMSgov',
                  id: '999888777',
                  url: 'https://api.github.com/orgs/CMSgov'
                }
              }
            ]
          )
          visit new_internal_user_session_path

          find('[data-test="internal-user-sign-in-form"]').click
          expect(page).to have_css('[data-test="internal-user-sign-in-form"]')
          expect(page).not_to have_css('[data-test="internal-user-signout"]')
        end
      end

      context 'when github auth fails' do
        scenario 'user gets redirected to sign-in page' do
          OmniAuth.config.mock_auth[:github] = :invalid_credentials

          visit new_internal_user_session_path

          find('[data-test="internal-user-sign-in-form"]').click
          expect(page).to have_css('[data-test="internal-user-sign-in-form"]')
          expect(page).not_to have_css('[data-test="internal-user-signout"]')
        end
      end
    end
  end


  context 'with Okta Oauth' do
    around do |example|
      OmniAuth.config.test_mode = true

      ClimateControl.modify INTERNAL_AUTH_PROVIDER: 'oktaoauth', OKTA_ADMIN_ROLE: 'DPC_AppRole_Admin' do
        example.run
      end

      OmniAuth.config.mock_auth[:oktaoauth] = nil
    end

    before(:each) do
      stub_const('InternalUser::OKTA_AUTH_ENABLED', true)
      stub_const('InternalUser::GITHUB_AUTH_ENABLED', false)
    end

    context 'successful auth' do
      scenario 'creates new internal user' do
        mock_oktaoauth(email: 'nemo@example.com')

        visit new_internal_user_session_path
        find('[data-test="internal-user-sign-in-form"]').click

        expect(page).to have_css('[data-test="internal-user-signout"]')
        expect(page).to have_content('nemo@example.com')
      end

      scenario 'logs in returning internal user' do
        mock_oktaoauth(uid: '00u2ysph7s90zebsv333', name: 'Found Nemo')
        internal_user = create(:internal_user, provider: 'oktaoauth', uid: '00u2ysph7s90zebsv333', name: 'Found Nemo')

        visit new_internal_user_session_path
        find('[data-test="internal-user-sign-in-form"]').click

        expect(page).to have_css('[data-test="internal-user-signout"]')
        expect(page).to have_content(internal_user.email)
      end
    end

    context 'failed auth' do
      context 'insufficient privileges' do
        scenario 'user sees error and is not signed in' do
          mock_oktaoauth(roles: ['BadRole'])

          visit new_internal_user_session_path
          find('[data-test="internal-user-sign-in-form"]').click

          expect(page).to have_content('Must have admin role')
          expect(page).to have_css('[data-test="internal-user-sign-in-form"]')
          expect(page).not_to have_css('[data-test="internal-user-signout"]')
        end
      end

      context 'oauth failure' do
        scenario 'user gets redirected to sign-in page' do
          OmniAuth.config.mock_auth[:oktaoauth] = :invalid_credentials

          visit new_internal_user_session_path

          find('[data-test="internal-user-sign-in-form"]').click
          expect(page).to have_css('[data-test="internal-user-sign-in-form"]')
          expect(page).not_to have_css('[data-test="internal-user-signout"]')
        end
      end
    end
  end
end
