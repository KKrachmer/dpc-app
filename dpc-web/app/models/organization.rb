# frozen_string_literal: true

class Organization < ApplicationRecord
  include OrganizationTypable

  has_one :address, as: :addressable, dependent: :destroy
  has_many :organization_user_assignments, dependent: :destroy
  has_many :users, through: :organization_user_assignments
  has_many :registered_organizations, dependent: :destroy

  enum organization_type: ORGANIZATION_TYPES

  validates :organization_type, inclusion: { in: ORGANIZATION_TYPES.keys }
  validates :name, uniqueness: true, presence: true
  validates :npi, uniqueness: { allow_blank: true }

  delegate :street, :street_2, :city, :state, :zip, to: :address, allow_nil: true, prefix: true
  accepts_nested_attributes_for :address, reject_if: :all_blank

  before_save :assign_id, if: -> { prod_sbx? }

  after_update :update_registered_organizations

  scope :vendor, -> { where(organization_type: ORGANIZATION_TYPES['health_it_vendor']) }
  scope :provider, -> { where.not(organization_type: ORGANIZATION_TYPES['health_it_vendor']) }

  def address_type
    address&.address_type
  end

  def address_use
    address&.address_use
  end

  def npi=(input)
    super(input.blank? ? nil : input)
  end

  def api_credentialable?
    registered_organizations.count.positive?
  end

  def assign_id
    return true if sandbox_id.present?

    self.sandbox_id = generate_sandbox_id
  end

  def external_identifier
    return sandbox_id if prod_sbx?

    npi
  end

  def registered_api_envs
    registered_organizations.pluck(:api_env)
  end

  def notify_users_of_sandbox_access
    return unless sandbox_enabled?

    organization_user_assignments.each(&:send_organization_sandbox_email)
  end

  def prod_sbx?
    ENV['DEPLOY_ENV'] == 'prod-sbx'
  end

  def update_registered_organizations
    return unless npi.present?

    registered_organizations.each(&:update_api_organization)
  end

  def sandbox_enabled?
    sandbox_registered_organization.present?
  end

  def sandbox_registered_organization
    registered_organizations.find_by(api_env: 'sandbox')
  end

  def sandbox_fhir_endpoint
    sandbox_registered_organization.fhir_endpoint
  end

  def production_enabled?
    production_registered_organization.present?
  end

  def production_registered_organization
    registered_organizations.find_by(api_env: 'production')
  end

  def production_fhir_endpoint
    production_registered_organization.fhir_endpoint
  end
end

private

def generate_sandbox_id
  loop do
    sandbox_id = Luhnacy.generate(15, prefix: '808403')[-10..-1]
    break sandbox_id unless Organization.where(sandbox_id: sandbox_id).exists?
  end
end
