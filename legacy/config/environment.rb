# Load the rails application
require File.expand_path('../application', __FILE__)

# Initialize the rails application
Demoapp::Application.initialize!

APP_CONFIG = HashWithIndifferentAccess.new(YAML.load(File.read(File.expand_path('../config.yml', __FILE__))))

